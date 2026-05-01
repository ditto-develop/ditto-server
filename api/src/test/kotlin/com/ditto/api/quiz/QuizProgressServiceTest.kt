package com.ditto.api.quiz

import com.ditto.api.quiz.dto.SubmitAnswerRequest
import com.ditto.api.quiz.service.QuizProgressService
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.domain.member.entity.Member
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
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import javax.sql.DataSource

class QuizProgressServiceTest(
    private val quizProgressService: QuizProgressService,
    private val quizSetRepository: QuizSetRepository,
    private val quizRepository: QuizRepository,
    private val quizChoiceRepository: QuizChoiceRepository,
    private val quizAnswerRepository: QuizAnswerRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val memberRepository: MemberRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val now = LocalDateTime.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))

    fun setupMember(nickname: String = "테스트유저"): Long {
        val member = memberRepository.save(Member(nickname = nickname))
        return member.id
    }

    fun setupActiveQuizData(): Triple<Long, Long, Long> {
        val quizSet = quizSetRepository.save(
            QuizSetFixture.create(startDate = now.minusDays(1), endDate = now.plusDays(1)),
        )
        val quiz = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id))
        val choice1 = quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id, content = "A", displayOrder = 1))
        quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id, content = "B", displayOrder = 2))
        return Triple(quiz.id, choice1.id, quizChoiceRepository.findByQuizIdInOrderByDisplayOrderAsc(listOf(quiz.id)).last().id)
    }

    "퀴즈 답안 제출" - {
        "정상적인 답안을 제출하면 QuizAnswer가 저장된다" {
            val memberId = setupMember()
            val (quizId, choiceId, _) = setupActiveQuizData()

            quizProgressService.submitAnswer(memberId, SubmitAnswerRequest(quizId, choiceId), now)

            val answer = quizAnswerRepository.findByMemberIdAndQuizId(1L, quizId)
            answer shouldNotBe null
            answer!!.choiceId shouldBe choiceId
        }

        "같은 퀴즈에 다른 선택지로 재제출하면 choiceId가 업데이트된다" {
            val memberId = setupMember()
            val quizSet = quizSetRepository.save(
                QuizSetFixture.create(startDate = now.minusDays(1), endDate = now.plusDays(1)),
            )
            val quiz1 = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id, displayOrder = 1))
            quizRepository.save(QuizFixture.create(quizSetId = quizSet.id, question = "두번째", displayOrder = 2))
            val choice1 = quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz1.id, content = "A", displayOrder = 1))
            val choice2 = quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz1.id, content = "B", displayOrder = 2))

            quizProgressService.submitAnswer(memberId, SubmitAnswerRequest(quiz1.id, choice1.id), now)
            quizProgressService.submitAnswer(memberId, SubmitAnswerRequest(quiz1.id, choice2.id), now)

            val answers = quizAnswerRepository.findAll()
            answers.size shouldBe 1
            answers[0].choiceId shouldBe choice2.id
        }

        "비활성 퀴즈 세트의 퀴즈에 답안을 제출하면 예외가 발생한다" {
            val memberId = setupMember()
            val quizSet = quizSetRepository.save(
                QuizSetFixture.create(startDate = now.minusDays(1), endDate = now.plusDays(1), isActive = false),
            )
            val quiz = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id))
            val choice = quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id))

            val exception = shouldThrow<ErrorException> {
                quizProgressService.submitAnswer(memberId, SubmitAnswerRequest(quiz.id, choice.id), now)
            }
            exception.errorCode shouldBe ErrorCode.QUIZ_NOT_IN_ACTIVE_SET
        }

        "기간이 지난 퀴즈 세트의 퀴즈에 답안을 제출하면 예외가 발생한다" {
            val memberId = setupMember()
            val quizSet = quizSetRepository.save(
                QuizSetFixture.create(startDate = now.minusDays(7), endDate = now.minusDays(1)),
            )
            val quiz = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id))
            val choice = quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id))

            val exception = shouldThrow<ErrorException> {
                quizProgressService.submitAnswer(memberId, SubmitAnswerRequest(quiz.id, choice.id), now)
            }
            exception.errorCode shouldBe ErrorCode.QUIZ_NOT_IN_ACTIVE_SET
        }

        "존재하지 않는 quizId로 제출하면 예외가 발생한다" {
            val memberId = setupMember()

            val exception = shouldThrow<ErrorException> {
                quizProgressService.submitAnswer(memberId, SubmitAnswerRequest(99999L, 1L), now)
            }
            exception.errorCode shouldBe ErrorCode.NOT_FOUND
        }

        "참여 불가 요일에 제출하면 예외가 발생한다" {
            val memberId = setupMember()
            val thursday = now.with(TemporalAdjusters.nextOrSame(DayOfWeek.THURSDAY))
            val quizSet = quizSetRepository.save(
                QuizSetFixture.create(startDate = thursday.minusDays(1), endDate = thursday.plusDays(1)),
            )
            val quiz = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id))
            val choice = quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id))
            val quizId = quiz.id
            val choiceId = choice.id

            val exception = shouldThrow<ErrorException> {
                quizProgressService.submitAnswer(memberId, SubmitAnswerRequest(quizId, choiceId), thursday)
            }
            exception.errorCode shouldBe ErrorCode.QUIZ_NOT_AVAILABLE_DAY
        }

        "COMPLETED 상태에서 답변을 수정하면 예외가 발생한다" {
            val memberId = setupMember()
            val quizSet = quizSetRepository.save(
                QuizSetFixture.create(startDate = now.minusDays(1), endDate = now.plusDays(7)),
            )
            val quiz = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id))
            val choice1 = quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id, content = "A", displayOrder = 1))
            val choice2 = quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id, content = "B", displayOrder = 2))

            quizProgressService.submitAnswer(memberId, SubmitAnswerRequest(quiz.id, choice1.id), now)

            val progress = quizProgressRepository.findByMemberIdAndQuizSetId(memberId, quizSet.id)
            progress!!.status shouldBe QuizProgressStatus.COMPLETED

            val exception = shouldThrow<ErrorException> {
                quizProgressService.submitAnswer(memberId, SubmitAnswerRequest(quiz.id, choice2.id), now)
            }
            exception.errorCode shouldBe ErrorCode.QUIZ_ALREADY_COMPLETED
        }

        "해당 퀴즈에 속하지 않는 choiceId로 제출하면 예외가 발생한다" {
            val memberId = setupMember()
            val (quizId, _, _) = setupActiveQuizData()

            val exception = shouldThrow<ErrorException> {
                quizProgressService.submitAnswer(memberId, SubmitAnswerRequest(quizId, 99999L), now)
            }
            exception.errorCode shouldBe ErrorCode.INVALID_CHOICE
        }

    }

    "퀴즈 진행률 조회" - {
        "퀴즈를 풀지 않았으면 NOT_STARTED를 반환한다" {
            val memberId = setupMember()
            quizSetRepository.save(
                QuizSetFixture.create(startDate = now.minusDays(1), endDate = now.plusDays(1)),
            )

            val result = quizProgressService.getProgress(memberId, now)

            result.status shouldBe QuizProgressStatus.NOT_STARTED
            result.quizSetId shouldBe null
        }

        "퀴즈를 일부 풀었으면 IN_PROGRESS를 반환한다" {
            val memberId = setupMember()
            val quizSet = quizSetRepository.save(
                QuizSetFixture.create(startDate = now.minusDays(1), endDate = now.plusDays(1)),
            )
            val quiz1 = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id, displayOrder = 1))
            quizRepository.save(QuizFixture.create(quizSetId = quizSet.id, question = "두번째", displayOrder = 2))
            val choice = quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz1.id))

            quizProgressService.submitAnswer(memberId, SubmitAnswerRequest(quiz1.id, choice.id), now)

            val result = quizProgressService.getProgress(memberId, now)

            result.status shouldBe QuizProgressStatus.IN_PROGRESS
            result.quizSetId shouldBe quizSet.id
            result.totalQuizzes shouldBe 2
            result.answeredQuizzes shouldBe 1
        }

        "퀴즈를 모두 풀었으면 COMPLETED를 반환한다" {
            val memberId = setupMember()
            val quizSet = quizSetRepository.save(
                QuizSetFixture.create(startDate = now.minusDays(1), endDate = now.plusDays(1)),
            )
            val quiz = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id))
            val choice = quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id))

            quizProgressService.submitAnswer(memberId, SubmitAnswerRequest(quiz.id, choice.id), now)

            val result = quizProgressService.getProgress(memberId, now)

            result.status shouldBe QuizProgressStatus.COMPLETED
            result.quizSetId shouldBe quizSet.id
            result.totalQuizzes shouldBe 1
            result.answeredQuizzes shouldBe 1
        }

        "participantCount가 정확하게 계산된다" {
            val memberId1 = setupMember("유저1")
            val memberId2 = setupMember("유저2")
            setupMember("유저3")

            val quizSet = quizSetRepository.save(
                QuizSetFixture.create(startDate = now.minusDays(1), endDate = now.plusDays(1)),
            )
            val quiz = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id))
            val choice = quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id))

            quizProgressService.submitAnswer(memberId1, SubmitAnswerRequest(quiz.id, choice.id), now)
            quizProgressService.submitAnswer(memberId2, SubmitAnswerRequest(quiz.id, choice.id), now)

            val result = quizProgressService.getProgress(memberId1, now)

            result.participantCount shouldBe 2
        }

        "활성 퀴즈셋이 없으면 예외가 발생한다" {
            val memberId = setupMember()

            val exception = shouldThrow<ErrorException> {
                quizProgressService.getProgress(memberId, now)
            }
            exception.errorCode shouldBe ErrorCode.NOT_FOUND
        }
    }

    "퀴즈셋 + 답변 조회" - {
        "퀴즈와 답변을 함께 조회한다" {
            val memberId = setupMember()
            val quizSet = quizSetRepository.save(
                QuizSetFixture.create(startDate = now.minusDays(1), endDate = now.plusDays(1)),
            )
            val quiz1 = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id, question = "첫번째", displayOrder = 1))
            val quiz2 = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id, question = "두번째", displayOrder = 2))
            val choice1 = quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz1.id, content = "A", displayOrder = 1))
            quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz1.id, content = "B", displayOrder = 2))
            quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz2.id, content = "C", displayOrder = 1))
            quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz2.id, content = "D", displayOrder = 2))

            quizProgressService.submitAnswer(memberId, SubmitAnswerRequest(quiz1.id, choice1.id), now)

            val result = quizProgressService.getQuizSetWithProgress(memberId, quizSet.id, now)

            result.totalCount shouldBe 2
            result.quizzes[0].userAnswer shouldBe choice1.id
            result.quizzes[1].userAnswer shouldBe null
        }

        "비활성 퀴즈셋을 조회하면 예외가 발생한다" {
            val memberId = setupMember()
            val quizSet = quizSetRepository.save(
                QuizSetFixture.create(startDate = now.minusDays(1), endDate = now.plusDays(1), isActive = false),
            )

            val exception = shouldThrow<ErrorException> {
                quizProgressService.getQuizSetWithProgress(memberId, quizSet.id, now)
            }
            exception.errorCode shouldBe ErrorCode.QUIZ_NOT_IN_ACTIVE_SET
        }

        "존재하지 않는 퀴즈셋을 조회하면 예외가 발생한다" {
            val memberId = setupMember()

            val exception = shouldThrow<ErrorException> {
                quizProgressService.getQuizSetWithProgress(memberId, 99999L, now)
            }
            exception.errorCode shouldBe ErrorCode.NOT_FOUND
        }
    }

    "퀴즈 진행 초기화" - {
        "초기화하면 답변과 진행 상태가 모두 삭제된다" {
            val memberId = setupMember()
            val quizSet = quizSetRepository.save(
                QuizSetFixture.create(startDate = now.minusDays(1), endDate = now.plusDays(1)),
            )
            val quiz = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id))
            val choice = quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id))

            quizProgressService.submitAnswer(memberId, SubmitAnswerRequest(quiz.id, choice.id), now)

            quizAnswerRepository.findAll().size shouldBe 1
            quizProgressRepository.findAll().size shouldBe 1

            quizProgressService.resetProgress(memberId, now)

            quizAnswerRepository.findAll().size shouldBe 0
            quizProgressRepository.findAll().size shouldBe 0
        }

        "초기화 후 진행률 조회하면 NOT_STARTED를 반환한다" {
            val memberId = setupMember()
            val quizSet = quizSetRepository.save(
                QuizSetFixture.create(startDate = now.minusDays(1), endDate = now.plusDays(1)),
            )
            val quiz = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id))
            val choice = quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id))

            quizProgressService.submitAnswer(memberId, SubmitAnswerRequest(quiz.id, choice.id), now)
            quizProgressService.resetProgress(memberId, now)

            val result = quizProgressService.getProgress(memberId, now)
            result.status shouldBe QuizProgressStatus.NOT_STARTED
        }

        "답변이 없어도 초기화 호출 시 예외가 발생하지 않는다" {
            val memberId = setupMember()
            quizSetRepository.save(
                QuizSetFixture.create(startDate = now.minusDays(1), endDate = now.plusDays(1)),
            )

            quizProgressService.resetProgress(memberId, now)
        }

        "활성 퀴즈셋이 없어도 초기화 호출 시 예외가 발생하지 않는다" {
            val memberId = setupMember()

            quizProgressService.resetProgress(memberId, now)
        }

        "다른 사용자의 답변은 초기화되지 않는다" {
            val memberId1 = setupMember("유저1")
            val memberId2 = setupMember("유저2")
            val quizSet = quizSetRepository.save(
                QuizSetFixture.create(startDate = now.minusDays(1), endDate = now.plusDays(1)),
            )
            val quiz = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id))
            val choice = quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id))

            quizProgressService.submitAnswer(memberId1, SubmitAnswerRequest(quiz.id, choice.id), now)
            quizProgressService.submitAnswer(memberId2, SubmitAnswerRequest(quiz.id, choice.id), now)

            quizProgressService.resetProgress(memberId1, now)

            quizAnswerRepository.findAll().size shouldBe 1
            quizProgressRepository.findAll().size shouldBe 1
        }
    }
})
