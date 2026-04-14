package com.ditto.api.quiz

import com.ditto.api.config.auth.MemberPrincipal
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
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.socialaccount.entity.SocialAccount
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.domain.socialaccount.repository.SocialAccountRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDateTime
import javax.sql.DataSource

class QuizProgressServiceTest(
    private val quizProgressService: QuizProgressService,
    private val quizSetRepository: QuizSetRepository,
    private val quizRepository: QuizRepository,
    private val quizChoiceRepository: QuizChoiceRepository,
    private val quizAnswerRepository: QuizAnswerRepository,
    private val memberRepository: MemberRepository,
    private val socialAccountRepository: SocialAccountRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val now = LocalDateTime.now()

    fun setupMember(providerUserId: String = "test-user"): MemberPrincipal {
        val member = memberRepository.save(Member(nickname = "테스트유저-$providerUserId"))
        socialAccountRepository.save(SocialAccount.create(member.id, SocialProvider.KAKAO, providerUserId))
        return MemberPrincipal(providerUserId = providerUserId, provider = SocialProvider.KAKAO)
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
            val principal = setupMember()
            val (quizId, choiceId, _) = setupActiveQuizData()

            quizProgressService.submitAnswer(principal, SubmitAnswerRequest(quizId, choiceId), now)

            val answer = quizAnswerRepository.findByMemberIdAndQuizId(1L, quizId)
            answer shouldNotBe null
            answer!!.choiceId shouldBe choiceId
        }

        "같은 퀴즈에 다른 선택지로 재제출하면 choiceId가 업데이트된다" {
            val principal = setupMember()
            val (quizId, choiceId1, choiceId2) = setupActiveQuizData()

            quizProgressService.submitAnswer(principal, SubmitAnswerRequest(quizId, choiceId1), now)
            quizProgressService.submitAnswer(principal, SubmitAnswerRequest(quizId, choiceId2), now)

            val answers = quizAnswerRepository.findAll()
            answers.size shouldBe 1
            answers[0].choiceId shouldBe choiceId2
        }

        "비활성 퀴즈 세트의 퀴즈에 답안을 제출하면 예외가 발생한다" {
            val principal = setupMember()
            val quizSet = quizSetRepository.save(
                QuizSetFixture.create(startDate = now.minusDays(1), endDate = now.plusDays(1), isActive = false),
            )
            val quiz = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id))
            val choice = quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id))

            val exception = shouldThrow<ErrorException> {
                quizProgressService.submitAnswer(principal, SubmitAnswerRequest(quiz.id, choice.id), now)
            }
            exception.errorCode shouldBe ErrorCode.QUIZ_NOT_IN_ACTIVE_SET
        }

        "기간이 지난 퀴즈 세트의 퀴즈에 답안을 제출하면 예외가 발생한다" {
            val principal = setupMember()
            val quizSet = quizSetRepository.save(
                QuizSetFixture.create(startDate = now.minusDays(7), endDate = now.minusDays(1)),
            )
            val quiz = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id))
            val choice = quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id))

            val exception = shouldThrow<ErrorException> {
                quizProgressService.submitAnswer(principal, SubmitAnswerRequest(quiz.id, choice.id), now)
            }
            exception.errorCode shouldBe ErrorCode.QUIZ_NOT_IN_ACTIVE_SET
        }

        "존재하지 않는 quizId로 제출하면 예외가 발생한다" {
            val principal = setupMember()

            val exception = shouldThrow<ErrorException> {
                quizProgressService.submitAnswer(principal, SubmitAnswerRequest(99999L, 1L), now)
            }
            exception.errorCode shouldBe ErrorCode.NOT_FOUND
        }

        "해당 퀴즈에 속하지 않는 choiceId로 제출하면 예외가 발생한다" {
            val principal = setupMember()
            val (quizId, _, _) = setupActiveQuizData()

            val exception = shouldThrow<ErrorException> {
                quizProgressService.submitAnswer(principal, SubmitAnswerRequest(quizId, 99999L), now)
            }
            exception.errorCode shouldBe ErrorCode.INVALID_CHOICE
        }

        "소셜 계정이 없는 principal로 제출하면 예외가 발생한다" {
            val (quizId, choiceId, _) = setupActiveQuizData()
            val unknownPrincipal = MemberPrincipal(providerUserId = "unknown", provider = SocialProvider.KAKAO)

            val exception = shouldThrow<ErrorException> {
                quizProgressService.submitAnswer(unknownPrincipal, SubmitAnswerRequest(quizId, choiceId), now)
            }
            exception.errorCode shouldBe ErrorCode.UNAUTHORIZED_ERROR
        }
    }

    "퀴즈 진행률 조회" - {
        "퀴즈를 풀지 않았으면 NOT_STARTED를 반환한다" {
            val principal = setupMember()
            quizSetRepository.save(
                QuizSetFixture.create(startDate = now.minusDays(1), endDate = now.plusDays(1)),
            )

            val result = quizProgressService.getProgress(principal, now)

            result.status shouldBe QuizProgressStatus.NOT_STARTED
            result.quizSetId shouldBe null
        }

        "퀴즈를 일부 풀었으면 IN_PROGRESS를 반환한다" {
            val principal = setupMember()
            val quizSet = quizSetRepository.save(
                QuizSetFixture.create(startDate = now.minusDays(1), endDate = now.plusDays(1)),
            )
            val quiz1 = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id, displayOrder = 1))
            quizRepository.save(QuizFixture.create(quizSetId = quizSet.id, question = "두번째", displayOrder = 2))
            val choice = quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz1.id))

            quizProgressService.submitAnswer(principal, SubmitAnswerRequest(quiz1.id, choice.id), now)

            val result = quizProgressService.getProgress(principal, now)

            result.status shouldBe QuizProgressStatus.IN_PROGRESS
            result.quizSetId shouldBe quizSet.id
            result.totalQuizzes shouldBe 2
            result.answeredQuizzes shouldBe 1
        }

        "퀴즈를 모두 풀었으면 COMPLETED를 반환한다" {
            val principal = setupMember()
            val quizSet = quizSetRepository.save(
                QuizSetFixture.create(startDate = now.minusDays(1), endDate = now.plusDays(1)),
            )
            val quiz = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id))
            val choice = quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id))

            quizProgressService.submitAnswer(principal, SubmitAnswerRequest(quiz.id, choice.id), now)

            val result = quizProgressService.getProgress(principal, now)

            result.status shouldBe QuizProgressStatus.COMPLETED
            result.quizSetId shouldBe quizSet.id
            result.totalQuizzes shouldBe 1
            result.answeredQuizzes shouldBe 1
        }

        "participantCount가 정확하게 계산된다" {
            val principal1 = setupMember("user-1")
            val principal2 = setupMember("user-2")
            setupMember("user-3")

            val quizSet = quizSetRepository.save(
                QuizSetFixture.create(startDate = now.minusDays(1), endDate = now.plusDays(1)),
            )
            val quiz = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id))
            val choice = quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id))

            quizProgressService.submitAnswer(principal1, SubmitAnswerRequest(quiz.id, choice.id), now)
            quizProgressService.submitAnswer(principal2, SubmitAnswerRequest(quiz.id, choice.id), now)

            val result = quizProgressService.getProgress(principal1, now)

            result.participantCount shouldBe 2
        }

        "활성 퀴즈셋이 없으면 예외가 발생한다" {
            val principal = setupMember()

            val exception = shouldThrow<ErrorException> {
                quizProgressService.getProgress(principal, now)
            }
            exception.errorCode shouldBe ErrorCode.NOT_FOUND
        }
    }

    "퀴즈셋 + 답변 조회" - {
        "퀴즈와 답변을 함께 조회한다" {
            val principal = setupMember()
            val quizSet = quizSetRepository.save(
                QuizSetFixture.create(startDate = now.minusDays(1), endDate = now.plusDays(1)),
            )
            val quiz1 = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id, question = "첫번째", displayOrder = 1))
            val quiz2 = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id, question = "두번째", displayOrder = 2))
            val choice1 = quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz1.id, content = "A", displayOrder = 1))
            quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz1.id, content = "B", displayOrder = 2))
            quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz2.id, content = "C", displayOrder = 1))
            quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz2.id, content = "D", displayOrder = 2))

            quizProgressService.submitAnswer(principal, SubmitAnswerRequest(quiz1.id, choice1.id), now)

            val result = quizProgressService.getQuizSetWithProgress(principal, quizSet.id, now)

            result.totalCount shouldBe 2
            result.quizzes[0].userAnswer shouldBe choice1.id
            result.quizzes[1].userAnswer shouldBe null
        }

        "비활성 퀴즈셋을 조회하면 예외가 발생한다" {
            val principal = setupMember()
            val quizSet = quizSetRepository.save(
                QuizSetFixture.create(startDate = now.minusDays(1), endDate = now.plusDays(1), isActive = false),
            )

            val exception = shouldThrow<ErrorException> {
                quizProgressService.getQuizSetWithProgress(principal, quizSet.id, now)
            }
            exception.errorCode shouldBe ErrorCode.QUIZ_NOT_IN_ACTIVE_SET
        }

        "존재하지 않는 퀴즈셋을 조회하면 예외가 발생한다" {
            val principal = setupMember()

            val exception = shouldThrow<ErrorException> {
                quizProgressService.getQuizSetWithProgress(principal, 99999L, now)
            }
            exception.errorCode shouldBe ErrorCode.NOT_FOUND
        }
    }
})
