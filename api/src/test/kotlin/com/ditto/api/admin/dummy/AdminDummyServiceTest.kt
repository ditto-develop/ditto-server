package com.ditto.api.admin.dummy

import com.ditto.api.admin.dummy.dto.DummyGenerateForm
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.match.entity.MatchCandidate
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.GenderPreference
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.QuizChoiceFixture
import com.ditto.domain.quiz.QuizFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.entity.QuizProgressStatus
import com.ditto.domain.quiz.repository.QuizAnswerRepository
import com.ditto.domain.quiz.repository.QuizChoiceRepository
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import javax.sql.DataSource

class AdminDummyServiceTest(
    private val adminDummyService: AdminDummyService,
    private val quizSetRepository: QuizSetRepository,
    private val quizRepository: QuizRepository,
    private val quizChoiceRepository: QuizChoiceRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val quizAnswerRepository: QuizAnswerRepository,
    private val memberRepository: MemberRepository,
    private val matchCandidateRepository: MatchCandidateRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    // 문항마다 선택지 2개를 가진 퀴즈셋을 만들고 id 를 반환한다.
    fun setupQuizSet(quizCount: Int = 3): Long {
        val quizSet = quizSetRepository.save(QuizSetFixture.create())
        repeat(quizCount) { index ->
            val quiz = quizRepository.save(
                QuizFixture.create(quizSetId = quizSet.id, question = "질문$index", displayOrder = index + 1),
            )
            quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id, content = "A", displayOrder = 1))
            quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id, content = "B", displayOrder = 2))
        }
        return quizSet.id
    }

    "더미 생성" - {
        "남/여 인원수만큼 ACTIVE 더미 회원이 생성된다" {
            val quizSetId = setupQuizSet()

            val created = adminDummyService.generate(
                DummyGenerateForm(quizSetId = quizSetId, maleCount = 2, femaleCount = 3),
            )

            created shouldBe 5
            val dummies = memberRepository.findByNicknameStartingWith("dummy-")
            dummies.size shouldBe 5
            dummies.count { it.gender == Gender.MALE } shouldBe 2
            dummies.count { it.gender == Gender.FEMALE } shouldBe 3
            dummies.forEach {
                it.status shouldBe MemberStatus.ACTIVE
                it.age shouldNotBe null
                it.location shouldNotBe null
                it.caricature shouldNotBe null
            }
        }

        "각 더미는 해당 퀴즈셋을 COMPLETED 로 풀고 문항 수만큼 답변을 남긴다" {
            val quizSetId = setupQuizSet(quizCount = 3)

            adminDummyService.generate(
                DummyGenerateForm(quizSetId = quizSetId, maleCount = 1, femaleCount = 1, preferredGender = GenderPreference.SAME),
            )

            val quizIds = quizRepository.findByQuizSetIdOrderByDisplayOrderAsc(quizSetId).map { it.id }
            memberRepository.findByNicknameStartingWith("dummy-").forEach { dummy ->
                val progress = quizProgressRepository.findByMemberIdAndQuizSetId(dummy.id, quizSetId)
                progress shouldNotBe null
                progress!!.status shouldBe QuizProgressStatus.COMPLETED
                progress.preferredGender shouldBe GenderPreference.SAME
                quizAnswerRepository.findByMemberIdAndQuizIdIn(dummy.id, quizIds).size shouldBe 3
            }
        }

        "각 답변의 choiceId 는 해당 문항의 선택지 중 하나다" {
            val quizSetId = setupQuizSet(quizCount = 3)
            adminDummyService.generate(DummyGenerateForm(quizSetId = quizSetId, maleCount = 1, femaleCount = 0))

            val quizzes = quizRepository.findByQuizSetIdOrderByDisplayOrderAsc(quizSetId)
            val choiceIdsByQuizId = quizzes.associate { quiz ->
                quiz.id to quizChoiceRepository.findByQuizIdOrderByDisplayOrderAsc(quiz.id).map { it.id }.toSet()
            }
            val dummy = memberRepository.findByNicknameStartingWith("dummy-").first()
            quizAnswerRepository.findByMemberIdAndQuizIdIn(dummy.id, quizzes.map { it.id }).forEach { answer ->
                (answer.choiceId in choiceIdsByQuizId.getValue(answer.quizId)) shouldBe true
            }
        }

        "나이는 지정한 범위 안에서 채워진다" {
            val quizSetId = setupQuizSet(quizCount = 1)
            adminDummyService.generate(
                DummyGenerateForm(quizSetId = quizSetId, maleCount = 5, femaleCount = 0, minAge = 25, maxAge = 27),
            )

            memberRepository.findByNicknameStartingWith("dummy-").forEach {
                (it.age!! in 25..27) shouldBe true
            }
        }
    }

    "더미 생성 입력 검증" - {
        "인원수가 모두 0이면 예외가 발생한다" {
            val quizSetId = setupQuizSet()

            val exception = shouldThrow<WarnException> {
                adminDummyService.generate(DummyGenerateForm(quizSetId = quizSetId, maleCount = 0, femaleCount = 0))
            }
            exception.errorCode shouldBe ErrorCode.BAD_REQUEST
        }

        "최소 나이가 최대 나이보다 크면 예외가 발생한다" {
            val quizSetId = setupQuizSet()

            val exception = shouldThrow<WarnException> {
                adminDummyService.generate(
                    DummyGenerateForm(quizSetId = quizSetId, maleCount = 1, femaleCount = 0, minAge = 30, maxAge = 20),
                )
            }
            exception.errorCode shouldBe ErrorCode.BAD_REQUEST
        }

        "존재하지 않는 퀴즈셋이면 NOT_FOUND 예외가 발생한다" {
            val exception = shouldThrow<WarnException> {
                adminDummyService.generate(DummyGenerateForm(quizSetId = 99999L, maleCount = 1, femaleCount = 1))
            }
            exception.errorCode shouldBe ErrorCode.NOT_FOUND
        }

        "문항이 없는 퀴즈셋이면 BAD_REQUEST 예외가 발생한다" {
            val quizSet = quizSetRepository.save(QuizSetFixture.create())

            val exception = shouldThrow<WarnException> {
                adminDummyService.generate(DummyGenerateForm(quizSetId = quizSet.id, maleCount = 1, femaleCount = 1))
            }
            exception.errorCode shouldBe ErrorCode.BAD_REQUEST
        }
    }

    "더미 일괄 삭제" - {
        "더미 회원과 그 진행·답변을 모두 삭제하고 삭제 수를 반환한다" {
            val quizSetId = setupQuizSet()
            adminDummyService.generate(DummyGenerateForm(quizSetId = quizSetId, maleCount = 2, femaleCount = 1))

            val deleted = adminDummyService.deleteAllDummies()

            deleted shouldBe 3
            memberRepository.findByNicknameStartingWith("dummy-").size shouldBe 0
            quizProgressRepository.findAll().size shouldBe 0
            quizAnswerRepository.findAll().size shouldBe 0
        }

        "더미가 포함된 매칭 후보도 함께 삭제된다" {
            val quizSetId = setupQuizSet()
            adminDummyService.generate(DummyGenerateForm(quizSetId = quizSetId, maleCount = 1, femaleCount = 1))
            val dummies = memberRepository.findByNicknameStartingWith("dummy-")
            matchCandidateRepository.save(
                MatchCandidate.create(dummies[0].id, dummies[1].id, quizSetId, 50.0, 1, 2),
            )
            matchCandidateRepository.save(
                MatchCandidate.create(dummies[1].id, dummies[0].id, quizSetId, 50.0, 1, 2),
            )

            adminDummyService.deleteAllDummies()

            matchCandidateRepository.findAll().size shouldBe 0
        }

        "더미가 아닌 회원은 삭제되지 않는다" {
            val real = memberRepository.save(Member(nickname = "진짜유저"))
            val quizSetId = setupQuizSet()
            adminDummyService.generate(DummyGenerateForm(quizSetId = quizSetId, maleCount = 1, femaleCount = 1))

            adminDummyService.deleteAllDummies()

            memberRepository.findById(real.id).isPresent shouldBe true
        }

        "더미가 없으면 0을 반환한다" {
            adminDummyService.deleteAllDummies() shouldBe 0
        }
    }

    "더미 수 조회" - {
        "countDummies 가 마커로 식별된 더미 수를 반환한다" {
            val quizSetId = setupQuizSet()
            adminDummyService.generate(DummyGenerateForm(quizSetId = quizSetId, maleCount = 2, femaleCount = 2))

            adminDummyService.countDummies() shouldBe 4L
        }
    }
})
