package com.ditto.api.quiz

import com.ditto.api.quiz.dto.SubmitAnswerRequest
import com.ditto.api.quiz.service.QuizProgressService
import com.ditto.api.system.featureflag.FeatureFlagAdminService
import com.ditto.api.system.featureflag.FeatureFlags
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.domain.match.GroupMatchFixture
import com.ditto.domain.match.entity.GroupMatchMember
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.QuizChoiceFixture
import com.ditto.domain.quiz.QuizFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QuizProgressStatus
import com.ditto.domain.quiz.repository.QuizAnswerRepository
import com.ditto.domain.quiz.repository.QuizChoiceRepository
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.common.exception.WarnException
import com.ditto.domain.sanction.SanctionFixture
import com.ditto.domain.sanction.entity.SanctionLevel
import com.ditto.domain.sanction.repository.SanctionRepository
import com.ditto.domain.system.entity.FeatureFlagKey
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.DayOfWeek
import java.time.LocalDate
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
    private val sanctionRepository: SanctionRepository,
    private val featureFlags: FeatureFlags,
    private val featureFlagAdminService: FeatureFlagAdminService,
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    beforeTest { featureFlags.evict() }
    afterTest { featureFlags.evict() }

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
        "1차 제재(경고) 차단 구간에는 답안을 제출할 수 없다" {
            val memberId = setupMember("경고제재유저")
            val (quizId, choiceId, _) = setupActiveQuizData()
            sanctionRepository.save(
                SanctionFixture.create(
                    memberId = memberId,
                    level = SanctionLevel.WARNING,
                    startsAt = now.minusDays(1),
                    endsAt = now.plusDays(6),
                ),
            )

            val exception = shouldThrow<WarnException> {
                quizProgressService.submitAnswer(memberId, SubmitAnswerRequest(quizId, choiceId), now)
            }

            exception.errorCode shouldBe ErrorCode.QUIZ_BLOCKED_BY_SANCTION
        }

        "차단 구간이 지난 경고는 제출을 막지 않는다" {
            val memberId = setupMember("만료경고유저")
            val (quizId, choiceId, _) = setupActiveQuizData()
            sanctionRepository.save(
                SanctionFixture.create(
                    memberId = memberId,
                    level = SanctionLevel.WARNING,
                    startsAt = now.minusDays(14),
                    endsAt = now.minusDays(7),
                ),
            )

            quizProgressService.submitAnswer(memberId, SubmitAnswerRequest(quizId, choiceId), now)

            quizAnswerRepository.findByMemberIdAndQuizId(memberId, quizId) shouldNotBe null
        }

        "1차 제재(경고) 차단 구간에는 진행 초기화도 할 수 없다" {
            val memberId = setupMember("경고초기화유저")
            setupActiveQuizData()
            sanctionRepository.save(
                SanctionFixture.create(
                    memberId = memberId,
                    level = SanctionLevel.WARNING,
                    startsAt = now.minusDays(1),
                    endsAt = now.plusDays(6),
                ),
            )

            val exception = shouldThrow<WarnException> {
                quizProgressService.resetProgress(memberId, now)
            }

            exception.errorCode shouldBe ErrorCode.QUIZ_BLOCKED_BY_SANCTION
        }

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

        "마지막 문항을 낸 시각이 완주 시각으로 남는다" {
            val memberId = setupMember()
            val quizSet = quizSetRepository.save(
                QuizSetFixture.create(startDate = now.minusDays(1), endDate = now.plusDays(1)),
            )
            val quiz1 = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id, displayOrder = 1))
            val quiz2 = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id, question = "두번째", displayOrder = 2))
            val choice1 = quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz1.id))
            val choice2 = quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz2.id))
            val firstAnsweredAt = now.withNano(0)
            val lastAnsweredAt = firstAnsweredAt.plusHours(3)

            quizProgressService.submitAnswer(memberId, SubmitAnswerRequest(quiz1.id, choice1.id), firstAnsweredAt)
            quizProgressRepository.findByMemberIdAndQuizSetId(memberId, quizSet.id)!!.completedAt shouldBe null

            quizProgressService.submitAnswer(memberId, SubmitAnswerRequest(quiz2.id, choice2.id), lastAnsweredAt)
            quizProgressRepository.findByMemberIdAndQuizSetId(memberId, quizSet.id)!!.completedAt shouldBe lastAnsweredAt
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

    "한 주에는 한 유형만 완주" - {
        fun setupQuizSet(matchingType: MatchingType, quizCount: Int): List<QuizWithChoice> {
            val quizSet = quizSetRepository.save(
                QuizSetFixture.create(
                    startDate = now.minusDays(1),
                    endDate = now.plusDays(1),
                    matchingType = matchingType,
                ),
            )
            return (1..quizCount).map { order ->
                val quiz = quizRepository.save(
                    QuizFixture.create(quizSetId = quizSet.id, question = "질문$order", displayOrder = order),
                )
                val choice = quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id))
                QuizWithChoice(quizId = quiz.id, choiceId = choice.id)
            }
        }

        fun submit(memberId: Long, quizWithChoice: QuizWithChoice) {
            val request = SubmitAnswerRequest(quizWithChoice.quizId, quizWithChoice.choiceId)
            quizProgressService.submitAnswer(memberId, request, now)
        }

        "다른 유형을 완주했으면 답을 낼 수 없다" {
            val memberId = setupMember()
            val oneToOneQuizzes = setupQuizSet(MatchingType.ONE_TO_ONE, quizCount = 1)
            val groupQuizzes = setupQuizSet(MatchingType.GROUP, quizCount = 1)
            submit(memberId, oneToOneQuizzes[0])

            val exception = shouldThrow<WarnException> { submit(memberId, groupQuizzes[0]) }

            exception.errorCode shouldBe ErrorCode.QUIZ_OTHER_TYPE_ALREADY_COMPLETED
        }

        "다른 유형을 풀다 말고 새 유형에 답하면 풀던 답과 진행이 지워진다" {
            val memberId = setupMember()
            val oneToOneQuizzes = setupQuizSet(MatchingType.ONE_TO_ONE, quizCount = 2)
            val groupQuizzes = setupQuizSet(MatchingType.GROUP, quizCount = 2)
            submit(memberId, oneToOneQuizzes[0])

            submit(memberId, groupQuizzes[0])

            quizAnswerRepository.findByMemberIdAndQuizId(memberId, oneToOneQuizzes[0].quizId) shouldBe null
            quizProgressRepository.findByMemberId(memberId).size shouldBe 1
            val result = quizProgressService.getProgress(memberId, now)
            result.status shouldBe QuizProgressStatus.IN_PROGRESS
            result.answeredQuizzes shouldBe 1
        }

        "갈아탄 유형을 그대로 완주할 수 있다" {
            val memberId = setupMember()
            val oneToOneQuizzes = setupQuizSet(MatchingType.ONE_TO_ONE, quizCount = 2)
            val groupQuizzes = setupQuizSet(MatchingType.GROUP, quizCount = 2)
            submit(memberId, oneToOneQuizzes[0])

            submit(memberId, groupQuizzes[0])
            submit(memberId, groupQuizzes[1])

            quizProgressService.getProgress(memberId, now).status shouldBe QuizProgressStatus.COMPLETED
        }

        "다른 회원의 진행은 지우지 않는다" {
            val memberId = setupMember("갈아타는유저")
            val otherMemberId = setupMember("다른유저")
            val oneToOneQuizzes = setupQuizSet(MatchingType.ONE_TO_ONE, quizCount = 2)
            val groupQuizzes = setupQuizSet(MatchingType.GROUP, quizCount = 2)
            submit(otherMemberId, oneToOneQuizzes[0])
            submit(memberId, oneToOneQuizzes[0])

            submit(memberId, groupQuizzes[0])

            quizAnswerRepository.findByMemberIdAndQuizId(otherMemberId, oneToOneQuizzes[0].quizId) shouldNotBe null
            quizProgressRepository.findByMemberId(otherMemberId).size shouldBe 1
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

        "활성 퀴즈셋이 없으면 오류 대신 NOT_STARTED·참여자 0으로 응답한다" {
            val memberId = setupMember()

            val result = quizProgressService.getProgress(memberId, now)

            result.status shouldBe QuizProgressStatus.NOT_STARTED
            result.quizSetId shouldBe null
            result.participantCount shouldBe 0
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

    "시크릿 매칭 늦은 응시" - {
        // 2026-11-16(월) 시작 주. 응답 마감 수 23:59:59, 늦은 응시 마감 토 20:00. 플래그는 켜지 않으면 꺼져 있다.
        val monday = LocalDate.of(2026, 11, 16)
        val responseDeadline = monday.plusDays(2).atTime(23, 59, 59)
        val thursday = monday.plusDays(3).atTime(10, 0)
        val saturdayRound = monday.plusDays(5).atTime(20, 0)

        fun enableSecretMatching() =
            featureFlagAdminService.set(FeatureFlagKey.SECRET_MATCHING, enabled = true, adminMemberId = 1L)

        fun disableSecretMatching() =
            featureFlagAdminService.set(FeatureFlagKey.SECRET_MATCHING, enabled = false, adminMemberId = 1L)

        fun setupWeekQuizSet(matchingType: MatchingType, quizCount: Int = 1): Pair<Long, List<QuizWithChoice>> {
            val quizSet = quizSetRepository.save(
                QuizSetFixture.create(
                    startDate = monday.atStartOfDay(),
                    endDate = responseDeadline,
                    matchingType = matchingType,
                ),
            )
            val quizzes = (1..quizCount).map { order ->
                val quiz = quizRepository.save(
                    QuizFixture.create(quizSetId = quizSet.id, question = "질문$order", displayOrder = order),
                )
                val choice = quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id))
                QuizWithChoice(quizId = quiz.id, choiceId = choice.id)
            }
            return quizSet.id to quizzes
        }

        fun submitAt(memberId: Long, quizWithChoice: QuizWithChoice, at: LocalDateTime) =
            quizProgressService.submitAnswer(memberId, SubmitAnswerRequest(quizWithChoice.quizId, quizWithChoice.choiceId), at)

        "켜져 있으면 응답 마감 뒤 목요일에 1:1 을 완주할 수 있고, 마감 전 완주로 치지 않는다" {
            enableSecretMatching()
            val memberId = setupMember()
            val (quizSetId, quizzes) = setupWeekQuizSet(MatchingType.ONE_TO_ONE)

            submitAt(memberId, quizzes[0], thursday)

            val progress = quizProgressRepository.findByMemberIdAndQuizSetId(memberId, quizSetId)!!
            progress.status shouldBe QuizProgressStatus.COMPLETED
            progress.completedAt shouldBe thursday
            progress.isCompletedOnTime(responseDeadline) shouldBe false
        }

        "토 20:00 부터는 4007 로 거절하고 그 답은 저장하지 않는다. 그 전에 낸 답은 남는다" {
            enableSecretMatching()
            val memberId = setupMember()
            val (quizSetId, quizzes) = setupWeekQuizSet(MatchingType.ONE_TO_ONE, quizCount = 2)
            submitAt(memberId, quizzes[0], saturdayRound.minusMinutes(10))

            val exception = shouldThrow<WarnException> { submitAt(memberId, quizzes[1], saturdayRound) }

            exception.errorCode shouldBe ErrorCode.QUIZ_SECRET_MATCHING_CLOSED
            quizAnswerRepository.findByMemberIdAndQuizId(memberId, quizzes[1].quizId) shouldBe null
            quizAnswerRepository.findByMemberIdAndQuizId(memberId, quizzes[0].quizId) shouldNotBe null
            quizProgressRepository.findByMemberIdAndQuizSetId(memberId, quizSetId)!!.status shouldBe
                QuizProgressStatus.IN_PROGRESS
        }

        "꺼져 있으면 목요일 제출은 지금처럼 참여 불가 요일이다" {
            disableSecretMatching()
            val memberId = setupMember()
            val (_, quizzes) = setupWeekQuizSet(MatchingType.ONE_TO_ONE)

            val exception = shouldThrow<ErrorException> { submitAt(memberId, quizzes[0], thursday) }

            exception.errorCode shouldBe ErrorCode.QUIZ_NOT_AVAILABLE_DAY
        }

        "켜져 있어도 그룹 셋은 목요일에 받지 않는다" {
            enableSecretMatching()
            val memberId = setupMember()
            val (_, quizzes) = setupWeekQuizSet(MatchingType.GROUP)

            val exception = shouldThrow<ErrorException> { submitAt(memberId, quizzes[0], thursday) }

            exception.errorCode shouldBe ErrorCode.QUIZ_NOT_AVAILABLE_DAY
        }

        "켜져 있으면 목요일에 1:1 셋 문항을 이어 풀 수 있게 조회된다" {
            enableSecretMatching()
            val memberId = setupMember()
            val (quizSetId, _) = setupWeekQuizSet(MatchingType.ONE_TO_ONE)

            quizProgressService.getQuizSetWithProgress(memberId, quizSetId, thursday).totalCount shouldBe 1
        }

        "켜져 있어도 토 20:00 부터는 문항 조회를 4007 로 거절한다" {
            enableSecretMatching()
            val memberId = setupMember()
            val (quizSetId, _) = setupWeekQuizSet(MatchingType.ONE_TO_ONE)

            val exception = shouldThrow<WarnException> {
                quizProgressService.getQuizSetWithProgress(memberId, quizSetId, saturdayRound)
            }

            exception.errorCode shouldBe ErrorCode.QUIZ_SECRET_MATCHING_CLOSED
        }

        "꺼져 있으면 목요일 문항 조회는 지금처럼 활성 셋이 아니다" {
            disableSecretMatching()
            val memberId = setupMember()
            val (quizSetId, _) = setupWeekQuizSet(MatchingType.ONE_TO_ONE)

            val exception = shouldThrow<ErrorException> {
                quizProgressService.getQuizSetWithProgress(memberId, quizSetId, thursday)
            }

            exception.errorCode shouldBe ErrorCode.QUIZ_NOT_IN_ACTIVE_SET
        }

    }

    "시크릿 매칭 그룹 완주자 1:1 예외" - {
        val monday = LocalDate.of(2026, 11, 16)
        val thursday = monday.plusDays(3).atTime(10, 0)
        val friday = monday.plusDays(4).atTime(10, 0)

        fun enableSecretMatching() =
            featureFlagAdminService.set(FeatureFlagKey.SECRET_MATCHING, enabled = true, adminMemberId = 1L)

        fun setupWeekQuizSet(matchingType: MatchingType): Pair<Long, List<QuizWithChoice>> {
            val quizSet = quizSetRepository.save(
                QuizSetFixture.create(
                    startDate = monday.atStartOfDay(),
                    endDate = monday.plusDays(2).atTime(23, 59, 59),
                    matchingType = matchingType,
                ),
            )
            val quiz = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id))
            val choice = quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id))
            return quizSet.id to listOf(QuizWithChoice(quizId = quiz.id, choiceId = choice.id))
        }

        fun submitAt(memberId: Long, quizWithChoice: QuizWithChoice, at: LocalDateTime) =
            quizProgressService.submitAnswer(memberId, SubmitAnswerRequest(quizWithChoice.quizId, quizWithChoice.choiceId), at)

        fun completeGroupQuiz(memberId: Long): Long {
            val (groupQuizSetId, groupQuizzes) = setupWeekQuizSet(MatchingType.GROUP)
            submitAt(memberId, groupQuizzes[0], monday.atTime(10, 0))
            return groupQuizSetId
        }

        fun inviteToGroup(groupQuizSetId: Long, memberId: Long, acceptedCount: Int = 0): GroupMatchMember {
            val group = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = groupQuizSetId, acceptedCount = acceptedCount))
            return GroupMatchMember.candidate(group.id, memberId)
        }

        fun otherMembersGroupExists(groupQuizSetId: Long) {
            groupMatchMemberRepository.save(inviteToGroup(groupQuizSetId, setupMember(nickname = "다른그룹유저")))
        }

        fun oneToOneProgressStatus(memberId: Long, oneToOneQuizSetId: Long) =
            quizProgressRepository.findByMemberIdAndQuizSetId(memberId, oneToOneQuizSetId)?.status

        "그룹을 완주했지만 성사된 그룹에 수락하지 않았으면 금요일부터 1:1 을 풀 수 있고 그룹 답은 남는다" {
            enableSecretMatching()
            val memberId = setupMember()
            val groupQuizSetId = completeGroupQuiz(memberId)
            val (oneToOneQuizSetId, oneToOneQuizzes) = setupWeekQuizSet(MatchingType.ONE_TO_ONE)

            submitAt(memberId, oneToOneQuizzes[0], friday)

            quizProgressRepository.findByMemberIdAndQuizSetId(memberId, oneToOneQuizSetId)!!.status shouldBe
                QuizProgressStatus.COMPLETED
            quizProgressRepository.findByMemberIdAndQuizSetId(memberId, groupQuizSetId)!!.status shouldBe
                QuizProgressStatus.COMPLETED
        }

        "셋에 후보 그룹이 아직 없으면 금요일 전에는 1:1 을 풀 수 없다" {
            enableSecretMatching()
            val memberId = setupMember()
            completeGroupQuiz(memberId)
            val (_, oneToOneQuizzes) = setupWeekQuizSet(MatchingType.ONE_TO_ONE)

            val exception = shouldThrow<WarnException> { submitAt(memberId, oneToOneQuizzes[0], thursday) }

            exception.errorCode shouldBe ErrorCode.QUIZ_OTHER_TYPE_ALREADY_COMPLETED
        }

        "배치 뒤 후보 그룹을 받지 못했으면 목요일에 바로 1:1 을 풀 수 있다" {
            enableSecretMatching()
            val memberId = setupMember()
            val groupQuizSetId = completeGroupQuiz(memberId)
            otherMembersGroupExists(groupQuizSetId)
            val (oneToOneQuizSetId, oneToOneQuizzes) = setupWeekQuizSet(MatchingType.ONE_TO_ONE)

            submitAt(memberId, oneToOneQuizzes[0], thursday)

            oneToOneProgressStatus(memberId, oneToOneQuizSetId) shouldBe QuizProgressStatus.COMPLETED
        }

        "그룹 초대를 거절했으면 목요일에 바로 1:1 을 풀 수 있다" {
            enableSecretMatching()
            val memberId = setupMember()
            val groupQuizSetId = completeGroupQuiz(memberId)
            groupMatchMemberRepository.save(inviteToGroup(groupQuizSetId, memberId).apply { decline() })
            val (oneToOneQuizSetId, oneToOneQuizzes) = setupWeekQuizSet(MatchingType.ONE_TO_ONE)

            submitAt(memberId, oneToOneQuizzes[0], thursday)

            oneToOneProgressStatus(memberId, oneToOneQuizSetId) shouldBe QuizProgressStatus.COMPLETED
        }

        "그룹 초대에 아직 답하지 않았으면 금요일 전에는 1:1 을 풀 수 없다" {
            enableSecretMatching()
            val memberId = setupMember()
            val groupQuizSetId = completeGroupQuiz(memberId)
            groupMatchMemberRepository.save(inviteToGroup(groupQuizSetId, memberId))
            val (_, oneToOneQuizzes) = setupWeekQuizSet(MatchingType.ONE_TO_ONE)

            val exception = shouldThrow<WarnException> { submitAt(memberId, oneToOneQuizzes[0], thursday) }

            exception.errorCode shouldBe ErrorCode.QUIZ_OTHER_TYPE_ALREADY_COMPLETED
        }

        "수락하고 그룹 성사를 기다리면 목요일에는 못 풀고 금요일부터 풀 수 있다" {
            enableSecretMatching()
            val memberId = setupMember()
            val groupQuizSetId = completeGroupQuiz(memberId)
            groupMatchMemberRepository.save(inviteToGroup(groupQuizSetId, memberId, acceptedCount = 1).apply { accept() })
            val (oneToOneQuizSetId, oneToOneQuizzes) = setupWeekQuizSet(MatchingType.ONE_TO_ONE)

            val exception = shouldThrow<WarnException> { submitAt(memberId, oneToOneQuizzes[0], thursday) }
            submitAt(memberId, oneToOneQuizzes[0], friday)

            exception.errorCode shouldBe ErrorCode.QUIZ_OTHER_TYPE_ALREADY_COMPLETED
            oneToOneProgressStatus(memberId, oneToOneQuizSetId) shouldBe QuizProgressStatus.COMPLETED
        }

        "성사된 그룹에 수락했으면 금요일에도 1:1 을 풀 수 없다" {
            enableSecretMatching()
            val memberId = setupMember()
            val groupQuizSetId = completeGroupQuiz(memberId)
            groupMatchMemberRepository.save(inviteToGroup(groupQuizSetId, memberId, acceptedCount = 3).apply { accept() })
            val (_, oneToOneQuizzes) = setupWeekQuizSet(MatchingType.ONE_TO_ONE)

            val exception = shouldThrow<WarnException> { submitAt(memberId, oneToOneQuizzes[0], friday) }

            exception.errorCode shouldBe ErrorCode.QUIZ_OTHER_TYPE_ALREADY_COMPLETED
        }
    }
})

private data class QuizWithChoice(val quizId: Long, val choiceId: Long)
