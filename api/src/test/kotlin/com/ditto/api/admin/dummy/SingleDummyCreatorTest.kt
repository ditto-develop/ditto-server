package com.ditto.api.admin.dummy

import com.ditto.api.admin.dummy.dto.SingleDummyForm
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.Interest
import com.ditto.domain.member.entity.Job
import com.ditto.domain.member.entity.Location
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.QuizChoiceFixture
import com.ditto.domain.quiz.QuizFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.entity.QuizChoice
import com.ditto.domain.quiz.entity.QuizProgressStatus
import com.ditto.domain.quiz.repository.QuizAnswerRepository
import com.ditto.domain.quiz.repository.QuizChoiceRepository
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import javax.sql.DataSource

class SingleDummyCreatorTest(
    private val singleDummyCreator: SingleDummyCreator,
    private val quizSetRepository: QuizSetRepository,
    private val quizRepository: QuizRepository,
    private val quizChoiceRepository: QuizChoiceRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val quizAnswerRepository: QuizAnswerRepository,
    private val memberRepository: MemberRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    class QuizSetSetup(val quizSetId: Long, val choicesByOrder: List<List<QuizChoice>>)

    // 문항마다 선택지 2개(A, B)를 가진 퀴즈셋. choicesByOrder[i] 는 i+1 번째 문항의 선택지다.
    fun setupQuizSet(quizCount: Int = 3): QuizSetSetup {
        val quizSet = quizSetRepository.save(QuizSetFixture.create())
        val choicesByOrder = (1..quizCount).map { order ->
            val quiz = quizRepository.save(
                QuizFixture.create(quizSetId = quizSet.id, question = "질문$order", displayOrder = order),
            )
            listOf("A", "B").mapIndexed { index, content ->
                quizChoiceRepository.save(
                    QuizChoiceFixture.create(quizId = quiz.id, content = content, displayOrder = index + 1),
                )
            }
        }
        return QuizSetSetup(quizSet.id, choicesByOrder)
    }

    fun form(quizSetId: Long) = SingleDummyForm(quizSetId = quizSetId, interests = mutableSetOf(Interest.MUSIC))

    fun answeredChoiceIdsOf(memberId: Long, setup: QuizSetSetup): List<Long> {
        val quizIds = setup.choicesByOrder.map { it.first().quizId }
        val choiceIdByQuizId = quizAnswerRepository.findByMemberIdAndQuizIdIn(memberId, quizIds)
            .associate { it.quizId to it.choiceId }
        return quizIds.mapNotNull { choiceIdByQuizId[it] }
    }

    "더미 한 명 만들기" - {
        "지정한 프로필로 ACTIVE 더미를 만든다" {
            val setup = setupQuizSet()

            val dummy = singleDummyCreator.create(
                form(setup.quizSetId).apply {
                    nicknameSuffix = "고득점"
                    gender = Gender.FEMALE
                    age = 31
                    location = Location.BUSAN
                    job = Job.DESIGN
                    interests = mutableSetOf(Interest.TRAVEL, Interest.COOKING)
                    avatarNumber = 3
                },
            ).member

            dummy.nickname shouldBe "dummy-고득점"
            dummy.status shouldBe MemberStatus.ACTIVE
            dummy.gender shouldBe Gender.FEMALE
            dummy.age shouldBe 31
            dummy.location shouldBe Location.BUSAN
            dummy.job shouldBe Job.DESIGN
            dummy.interests shouldBe setOf(Interest.TRAVEL, Interest.COOKING)
            dummy.caricature shouldBe "/onboarding/profileimg/avatar/f3.svg"
        }

        "닉네임 뒷부분과 캐리커쳐를 비우면 자동으로 채운다" {
            val setup = setupQuizSet()

            val dummy = singleDummyCreator.create(form(setup.quizSetId).apply { gender = Gender.MALE }).member

            dummy.nickname shouldMatch Regex("dummy-male-[0-9a-f]{8}")
            dummy.caricature.shouldNotBeNull() shouldMatch Regex("/onboarding/profileimg/avatar/m[1-8]\\.svg")
        }

        "지정한 문항은 그 선택지로, 나머지는 문항의 선택지 중 하나로 답한다" {
            // 지정을 무시하고 무작위로 골라도 우연히 맞을 확률을 1/128 로 낮추려고 7개 문항을 지정한다.
            val setup = setupQuizSet(quizCount = 8)
            val chosenBs = setup.choicesByOrder.drop(1).map { it[1] }

            val dummy = singleDummyCreator.create(
                form(setup.quizSetId).apply {
                    choiceIdByQuizId = chosenBs.associate { it.quizId to it.id }.toMutableMap()
                },
            ).member

            val answered = answeredChoiceIdsOf(dummy.id, setup)
            answered.size shouldBe 8
            answered.drop(1) shouldBe chosenBs.map { it.id }
            (answered.first() in setup.choicesByOrder.first().map { it.id }) shouldBe true
            quizProgressRepository.findByMemberIdAndQuizSetId(dummy.id, setup.quizSetId)
                .shouldNotBeNull().status shouldBe QuizProgressStatus.COMPLETED
        }

        "푼 문항 수를 주면 앞에서부터 그만큼만 답하고 진행 중으로 둔다" {
            val setup = setupQuizSet(quizCount = 3)

            val created = singleDummyCreator.create(form(setup.quizSetId).apply { answeredCount = 2 })
            val dummy = created.member

            val answeredQuizIds = quizAnswerRepository
                .findByMemberIdAndQuizIdIn(dummy.id, setup.choicesByOrder.map { it.first().quizId })
                .map { it.quizId }
            answeredQuizIds.toSet() shouldBe setup.choicesByOrder.take(2).map { it.first().quizId }.toSet()
            val progress = quizProgressRepository.findByMemberIdAndQuizSetId(dummy.id, setup.quizSetId)
                .shouldNotBeNull()
            progress.status shouldBe QuizProgressStatus.IN_PROGRESS
            progress.answeredCount shouldBe 2
            created.toDisplayText() shouldBe "${dummy.nickname} (#${dummy.id} · 남성 · 2/3 풀이)"
        }

        "푼 문항 수가 0이면 답과 진행 없이 회원만 만든다" {
            val setup = setupQuizSet()

            val dummy = singleDummyCreator.create(form(setup.quizSetId).apply { answeredCount = 0 }).member

            answeredChoiceIdsOf(dummy.id, setup).shouldBeEmpty()
            quizProgressRepository.findByMemberIdAndQuizSetId(dummy.id, setup.quizSetId) shouldBe null
        }
    }

    "더미 한 명 만들기 입력 검증" - {
        fun shouldReject(form: SingleDummyForm): WarnException {
            val exception = shouldThrow<WarnException> { singleDummyCreator.create(form) }
            memberRepository.findByNicknameStartingWith(DummyMarker.NICKNAME_PREFIX).shouldBeEmpty()
            return exception
        }

        "이미 있는 닉네임이면 거부한다" {
            val setup = setupQuizSet()
            memberRepository.save(MemberFixture.create(nickname = "dummy-중복"))

            val exception = shouldThrow<WarnException> {
                singleDummyCreator.create(form(setup.quizSetId).apply { nicknameSuffix = "중복" })
            }

            exception.message shouldBe "이미 있는 닉네임입니다: dummy-중복"
            memberRepository.countByNicknameStartingWith(DummyMarker.NICKNAME_PREFIX) shouldBe 1L
        }

        listOf<Pair<String, SingleDummyForm.() -> Unit>>(
            "닉네임 뒷부분에 허용하지 않는 문자가 있으면" to { nicknameSuffix = "a b" },
            "닉네임 뒷부분이 20자를 넘으면" to { nicknameSuffix = "a".repeat(21) },
            "나이를 비우면" to { age = null },
            "나이가 20 미만이면" to { age = 19 },
            "관심사가 없으면" to { interests = mutableSetOf() },
            "관심사가 5개를 넘으면" to { interests = Interest.entries.take(6).toMutableSet() },
            "캐리커쳐 번호가 범위를 벗어나면" to { avatarNumber = 9 },
            "푼 문항 수가 문항 수를 넘으면" to { answeredCount = 4 },
            "푼 문항 수가 음수면" to { answeredCount = -1 },
        ).forEach { (case, edit) ->
            "$case 거부하고 아무도 만들지 않는다" {
                val setup = setupQuizSet(quizCount = 3)

                shouldReject(form(setup.quizSetId).apply(edit)).errorCode shouldBe ErrorCode.BAD_REQUEST
            }
        }

        "다른 문항의 선택지를 지정하면 거부한다" {
            val setup = setupQuizSet()
            val otherQuizChoice = setup.choicesByOrder[1][0]
            val firstQuizId = setup.choicesByOrder[0][0].quizId

            val exception = shouldReject(
                form(setup.quizSetId).apply { choiceIdByQuizId = mutableMapOf(firstQuizId to otherQuizChoice.id) },
            )

            exception.errorCode shouldBe ErrorCode.BAD_REQUEST
        }

        "없는 퀴즈셋이면 NOT_FOUND 로 거부한다" {
            shouldReject(form(quizSetId = 999_999L)).errorCode shouldBe ErrorCode.NOT_FOUND
        }
    }
})
