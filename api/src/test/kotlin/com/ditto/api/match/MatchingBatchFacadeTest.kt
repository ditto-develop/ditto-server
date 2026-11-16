package com.ditto.api.match

import com.ditto.api.match.service.MatchingBatchFacade
import com.ditto.api.match.service.SecretRoundBatchResult
import com.ditto.api.system.featureflag.FeatureFlagAdminService
import com.ditto.api.system.featureflag.FeatureFlags
import com.ditto.domain.match.MatchCandidateFixture
import com.ditto.domain.match.entity.CandidateOrigin
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.GenderPreference
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.QuizAnswerFixture
import com.ditto.domain.quiz.QuizFixture
import com.ditto.domain.quiz.QuizProgressFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.repository.QuizAnswerRepository
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.system.entity.FeatureFlagKey
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource

class MatchingBatchFacadeTest(
    private val matchingBatchFacade: MatchingBatchFacade,
    private val memberRepository: MemberRepository,
    private val quizSetRepository: QuizSetRepository,
    private val quizRepository: QuizRepository,
    private val quizAnswerRepository: QuizAnswerRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val matchCandidateRepository: MatchCandidateRepository,
    private val groupMatchRepository: GroupMatchRepository,
    private val featureFlags: FeatureFlags,
    private val featureFlagAdminService: FeatureFlagAdminService,
    dataSource: DataSource,
) : IntegrationTest(
    dataSource,
    {
        beforeTest { featureFlags.evict() }
        afterTest { featureFlags.evict() }

        // 픽스처 퀴즈셋은 2026-04-12 23:59:59 에 끝난다 — 그 다음 목요일 00:01 배치 시각
        val batchAt = LocalDateTime.of(2026, 4, 16, 0, 1)

        /** 문항 하나짜리 퀴즈셋에 [nicknames] 회원이 모두 같은 답으로 완주한 상태를 만든다. 1:1 자격(활성 회원·성별·나이·선호)도 채운다. */
        fun saveEndedQuizSetCompletedBy(matchingType: MatchingType, nicknames: List<String>): Long {
            val quizSetId = quizSetRepository.save(QuizSetFixture.create(matchingType = matchingType)).id
            val quizId = quizRepository.save(QuizFixture.create(quizSetId = quizSetId, displayOrder = 1)).id
            nicknames.forEach { nickname ->
                val memberId = memberRepository.save(MemberFixture.create(nickname = nickname, status = MemberStatus.ACTIVE, gender = Gender.MALE, age = 25)).id
                quizAnswerRepository.save(QuizAnswerFixture.create(memberId = memberId, quizId = quizId, choiceId = 1L))
                val progress = QuizProgressFixture.create(memberId = memberId, quizSetId = quizSetId, totalCount = 1)
                progress.recordAnswer()
                progress.selectPreferredGender(GenderPreference.ANY)
                quizProgressRepository.save(progress)
            }
            return quizSetId
        }

        "runSecretRound" - {
            // 2026-11-16(월) 시작 주. 응답 마감 수 23:59:59, 1차 라운드 금 20:00, 2차 라운드 토 20:00.
            val monday = LocalDate.of(2026, 11, 16)
            val fridayRun = monday.plusDays(4).atTime(20, 1)
            val saturdayRun = monday.plusDays(5).atTime(20, 1)

            fun enableSecretMatching() =
                featureFlagAdminService.set(FeatureFlagKey.SECRET_MATCHING, enabled = true, adminMemberId = 1L)

            fun saveWeekQuizSet(matchingType: MatchingType = MatchingType.ONE_TO_ONE): Pair<Long, Long> {
                val quizSetId = quizSetRepository.save(
                    QuizSetFixture.create(
                        startDate = monday.atStartOfDay(),
                        endDate = monday.plusDays(2).atTime(23, 59, 59),
                        matchingType = matchingType,
                    ),
                ).id
                return quizSetId to quizRepository.save(QuizFixture.create(quizSetId = quizSetId, displayOrder = 1)).id
            }

            fun saveCompleted(quizSetId: Long, quizId: Long, nickname: String, completedAt: LocalDateTime? = null): Long {
                val memberId = memberRepository.save(
                    MemberFixture.create(nickname = nickname, status = MemberStatus.ACTIVE, gender = Gender.MALE, age = 25),
                ).id
                quizAnswerRepository.save(QuizAnswerFixture.create(memberId = memberId, quizId = quizId, choiceId = 1L))
                val progress = QuizProgressFixture.create(memberId = memberId, quizSetId = quizSetId, totalCount = 1)
                progress.recordAnswer(completedAt)
                progress.selectPreferredGender(GenderPreference.ANY)
                quizProgressRepository.save(progress)
                return memberId
            }

            fun originsOf(quizSetId: Long): List<CandidateOrigin> =
                matchCandidateRepository.findAll().filter { it.quizSetId == quizSetId }.map { it.candidateOrigin }

            "플래그가 꺼져 있으면 아무것도 하지 않는다" {
                val (quizSetId, quizId) = saveWeekQuizSet()
                listOf("꺼짐A", "꺼짐B").forEach { saveCompleted(quizSetId, quizId, it) }

                matchingBatchFacade.runSecretRound(fridayRun) shouldBe SecretRoundBatchResult.FLAG_OFF
                originsOf(quizSetId) shouldBe emptyList()
            }

            "켜져 있어도 1차 라운드 시각 전이면 아무것도 하지 않는다" {
                enableSecretMatching()
                val (quizSetId, quizId) = saveWeekQuizSet()
                listOf("이른A", "이른B").forEach { saveCompleted(quizSetId, quizId, it) }

                matchingBatchFacade.runSecretRound(fridayRun.minusMinutes(2)) shouldBe SecretRoundBatchResult.NO_ROUND_DUE
                originsOf(quizSetId) shouldBe emptyList()
            }

            "금 20:01 에는 정규 배치가 놓친 셋을 먼저 메우고, 그 주 1:1 셋에만 1차 라운드를 돌리며 정규 쌍은 다시 뽑지 않는다" {
                enableSecretMatching()
                val (oneToOneId, oneToOneQuizId) = saveWeekQuizSet()
                val (groupId, groupQuizId) = saveWeekQuizSet(MatchingType.GROUP)
                listOf("금A", "금B").forEach { saveCompleted(oneToOneId, oneToOneQuizId, it) }
                listOf("금그룹A", "금그룹B", "금그룹C").forEach { saveCompleted(groupId, groupQuizId, it) }

                val result = matchingBatchFacade.runSecretRound(fridayRun)

                result.backfilledRegularQuizSetIds.sorted() shouldBe listOf(oneToOneId, groupId).sorted()
                result.roundResults.map { it.quizSetId to it.secretRound } shouldBe listOf(oneToOneId to CandidateOrigin.SECRET_FIRST)
                originsOf(oneToOneId) shouldBe listOf(CandidateOrigin.REGULAR, CandidateOrigin.REGULAR)
            }

            "토 20:01 에는 2차 라운드로 금요일 뒤에 늦게 완주한 회원에게 후보를 더한다" {
                enableSecretMatching()
                val (quizSetId, quizId) = saveWeekQuizSet()
                val a = saveCompleted(quizSetId, quizId, "토A")
                val b = saveCompleted(quizSetId, quizId, "토B")
                matchCandidateRepository.saveAll(
                    listOf(
                        MatchCandidateFixture.create(ownerMemberId = a, otherMemberId = b, quizSetId = quizSetId),
                        MatchCandidateFixture.create(ownerMemberId = b, otherMemberId = a, quizSetId = quizSetId),
                    ),
                )
                val late = saveCompleted(quizSetId, quizId, "토늦은", completedAt = fridayRun.plusHours(1))

                val result = matchingBatchFacade.runSecretRound(saturdayRun)

                result.roundResults.single().secretRound shouldBe CandidateOrigin.SECRET_SECOND
                result.roundResults.single().newCandidateOwnerIds shouldBe setOf(a, b, late)
                originsOf(quizSetId).count { it == CandidateOrigin.SECRET_SECOND } shouldBe 4
            }
        }

        "runScheduledMatching" - {

            "마감된 퀴즈셋마다 후보를 만들고, 생성한 셋 ID만 돌려준다" {
                val oneToOneId = saveEndedQuizSetCompletedBy(MatchingType.ONE_TO_ONE, listOf("배치1:1A", "배치1:1B"))
                val groupId = saveEndedQuizSetCompletedBy(MatchingType.GROUP, listOf("배치그룹A", "배치그룹B", "배치그룹C"))
                // 아직 끝나지 않은 셋은 대상이 아니다
                val openId = quizSetRepository.save(
                    QuizSetFixture.create(endDate = LocalDateTime.of(2026, 4, 22, 23, 59, 59)),
                ).id

                val generatedIds = matchingBatchFacade.runScheduledMatching(batchAt)

                generatedIds.sorted() shouldBe listOf(oneToOneId, groupId).sorted()
                matchCandidateRepository.findAll().filter { it.quizSetId == oneToOneId } shouldHaveSize 2
                groupMatchRepository.findByQuizSetId(groupId) shouldHaveSize 1
                matchCandidateRepository.findAll().filter { it.quizSetId == openId } shouldHaveSize 0
            }

            "이미 후보가 있는 셋은 다시 처리하지 않는다 (멱등)" {
                val quizSetId = saveEndedQuizSetCompletedBy(MatchingType.ONE_TO_ONE, listOf("멱등A", "멱등B"))
                matchingBatchFacade.runScheduledMatching(batchAt) shouldBe listOf(quizSetId)

                matchingBatchFacade.runScheduledMatching(batchAt) shouldBe emptyList()
            }
        }
    },
)
