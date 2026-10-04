package com.ditto.api.admin.quiz

import com.ditto.api.admin.quiz.dto.QuizParticipantKind
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
import com.ditto.domain.quiz.QuizAnswerFixture
import com.ditto.domain.quiz.QuizChoiceFixture
import com.ditto.domain.quiz.QuizFixture
import com.ditto.domain.quiz.QuizProgressFixture
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
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import javax.sql.DataSource

class AdminQuizParticipantServiceTest(
    private val adminQuizParticipantService: AdminQuizParticipantService,
    private val quizSetRepository: QuizSetRepository,
    private val quizRepository: QuizRepository,
    private val quizChoiceRepository: QuizChoiceRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val quizAnswerRepository: QuizAnswerRepository,
    private val memberRepository: MemberRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    // 선택지 내용은 "Q{문항 순서}-A"·"Q{문항 순서}-B"다.
    fun setupQuizSetWithTwoChoicesPerQuiz(quizCount: Int): Pair<Long, List<List<QuizChoice>>> {
        val quizSet = quizSetRepository.save(QuizSetFixture.create())
        val choicesByQuizOrder = (1..quizCount).map { order ->
            val quiz = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id, question = "질문$order", displayOrder = order))
            listOf("A", "B").mapIndexed { index, label ->
                quizChoiceRepository.save(
                    QuizChoiceFixture.create(quizId = quiz.id, content = "Q$order-$label", displayOrder = index + 1),
                )
            }
        }
        return quizSet.id to choicesByQuizOrder
    }

    fun saveProgress(memberId: Long, quizSetId: Long, totalCount: Int, answeredCount: Int) {
        val progress = QuizProgressFixture.create(memberId = memberId, quizSetId = quizSetId, totalCount = totalCount)
        repeat(answeredCount) { progress.recordAnswer() }
        quizProgressRepository.save(progress)
    }

    fun saveAnswer(memberId: Long, choice: QuizChoice) {
        quizAnswerRepository.save(QuizAnswerFixture.create(memberId = memberId, quizId = choice.quizId, choiceId = choice.id))
    }

    "퀴즈셋 참여 현황" - {
        "참여자마다 진행과 프로필을 담고 더미와 실회원을 구분한다" {
            val (quizSetId, choices) = setupQuizSetWithTwoChoicesPerQuiz(quizCount = 1)
            val real = memberRepository.save(
                MemberFixture.create(
                    nickname = "실회원",
                    status = MemberStatus.ACTIVE,
                    gender = Gender.FEMALE,
                    age = 27,
                    interests = setOf(Interest.MUSIC, Interest.TRAVEL),
                    location = Location.SEOUL,
                    job = Job.DESIGN,
                    caricature = "/onboarding/profileimg/avatar/f3.svg",
                ),
            )
            val dummy = memberRepository.save(
                MemberFixture.create(nickname = "dummy-male-1a2b", email = "dummy@dummy.local", caricature = "dummy"),
            )
            saveProgress(real.id, quizSetId, totalCount = 1, answeredCount = 1)
            saveAnswer(real.id, choices[0][1])
            saveProgress(dummy.id, quizSetId, totalCount = 1, answeredCount = 0)

            val view = adminQuizParticipantService.getParticipants(quizSetId)

            view.participants.map { it.memberId } shouldContainExactly listOf(real.id, dummy.id)
            val (realRow, dummyRow) = view.participants
            realRow.kind shouldBe QuizParticipantKind.REAL
            realRow.memberStatus shouldBe MemberStatus.ACTIVE
            realRow.progressStatus shouldBe QuizProgressStatus.COMPLETED
            realRow.gender shouldBe Gender.FEMALE
            realRow.age shouldBe 27
            realRow.interests shouldBe setOf(Interest.MUSIC, Interest.TRAVEL)
            realRow.location shouldBe Location.SEOUL
            realRow.job shouldBe Job.DESIGN
            realRow.caricatureFileName shouldBe "f3"
            realRow.answerContents shouldContainExactly listOf("Q1-B")
            dummyRow.kind shouldBe QuizParticipantKind.DUMMY
            dummyRow.progressStatus shouldBe QuizProgressStatus.NOT_STARTED
            dummyRow.caricatureFileName shouldBe "dummy"
            view.completedCount shouldBe 1
            view.notStartedCount shouldBe 1
            view.dummyCount shouldBe 1
            view.realMemberCount shouldBe 1
        }

        "답변은 문항 순서대로 놓이고 안 푼 문항은 빈칸이다" {
            val (quizSetId, choices) = setupQuizSetWithTwoChoicesPerQuiz(quizCount = 3)
            val member = memberRepository.save(MemberFixture.create(nickname = "푸는중"))
            saveProgress(member.id, quizSetId, totalCount = 3, answeredCount = 2)
            saveAnswer(member.id, choices[1][0])
            saveAnswer(member.id, choices[0][1])

            val participant = adminQuizParticipantService.getParticipants(quizSetId).participants.single()

            participant.progressStatus shouldBe QuizProgressStatus.IN_PROGRESS
            participant.answeredCount shouldBe 2
            participant.answerContents shouldContainExactly listOf("Q1-B", "Q2-A", null)
        }

        "회원 행이 지워졌으면 삭제된 회원으로 남기고 프로필을 비운다" {
            val (quizSetId, choices) = setupQuizSetWithTwoChoicesPerQuiz(quizCount = 1)
            val deletedMemberId = 99999L
            saveProgress(deletedMemberId, quizSetId, totalCount = 1, answeredCount = 1)
            saveAnswer(deletedMemberId, choices[0][0])

            val participant = adminQuizParticipantService.getParticipants(quizSetId).participants.single()

            participant.kind shouldBe QuizParticipantKind.DELETED
            participant.nickname shouldBe null
            participant.interests.shouldBeEmpty()
            participant.answerContents shouldContainExactly listOf("Q1-A")
        }

        "삭제된 회원은 실회원 수에 들지 않고 따로 센다" {
            val (quizSetId, _) = setupQuizSetWithTwoChoicesPerQuiz(quizCount = 1)
            val real = memberRepository.save(MemberFixture.create(nickname = "실회원"))
            saveProgress(real.id, quizSetId, totalCount = 1, answeredCount = 0)
            saveProgress(99999L, quizSetId, totalCount = 1, answeredCount = 0)

            val view = adminQuizParticipantService.getParticipants(quizSetId)

            view.realMemberCount shouldBe 1
            view.dummyCount shouldBe 0
            view.deletedMemberCount shouldBe 1
        }

        "실회원을 먼저 두고 같은 구분 안에서는 참여 순서를 지킨다" {
            val (quizSetId, _) = setupQuizSetWithTwoChoicesPerQuiz(quizCount = 1)
            val firstDummy = memberRepository.save(MemberFixture.create(nickname = "dummy-male-0001", email = "d1@dummy.local"))
            val firstReal = memberRepository.save(MemberFixture.create(nickname = "먼저온회원", email = "r1@example.com"))
            val secondDummy = memberRepository.save(MemberFixture.create(nickname = "dummy-male-0002", email = "d2@dummy.local"))
            val secondReal = memberRepository.save(MemberFixture.create(nickname = "나중온회원", email = "r2@example.com"))
            listOf(firstDummy, firstReal, secondDummy, secondReal).forEach {
                saveProgress(it.id, quizSetId, totalCount = 1, answeredCount = 0)
            }

            val participants = adminQuizParticipantService.getParticipants(quizSetId).participants

            participants.map { it.memberId } shouldContainExactly
                listOf(firstReal.id, secondReal.id, firstDummy.id, secondDummy.id)
        }

        "답한 선택지가 지워졌으면 빈칸 대신 삭제된 선택지로 표시한다" {
            val (quizSetId, choices) = setupQuizSetWithTwoChoicesPerQuiz(quizCount = 1)
            val member = memberRepository.save(MemberFixture.create(nickname = "선택지삭제"))
            saveProgress(member.id, quizSetId, totalCount = 1, answeredCount = 1)
            val deletedChoice = choices[0][0]
            saveAnswer(member.id, deletedChoice)
            quizChoiceRepository.delete(deletedChoice)

            val participant = adminQuizParticipantService.getParticipants(quizSetId).participants.single()

            participant.answerContents shouldContainExactly listOf("(삭제된 선택지 #${deletedChoice.id})")
        }

        "다른 퀴즈셋의 진행과 답변은 담지 않는다" {
            val (quizSetId, _) = setupQuizSetWithTwoChoicesPerQuiz(quizCount = 1)
            val (otherQuizSetId, otherChoices) = setupQuizSetWithTwoChoicesPerQuiz(quizCount = 1)
            val member = memberRepository.save(MemberFixture.create(nickname = "다른주"))
            saveProgress(member.id, otherQuizSetId, totalCount = 1, answeredCount = 1)
            saveAnswer(member.id, otherChoices[0][0])

            adminQuizParticipantService.getParticipants(quizSetId).participants.shouldBeEmpty()
        }

        "없는 퀴즈셋이면 NOT_FOUND 예외가 발생한다" {
            val exception = shouldThrow<WarnException> { adminQuizParticipantService.getParticipants(99999L) }

            exception.errorCode shouldBe ErrorCode.NOT_FOUND
        }
    }
})
