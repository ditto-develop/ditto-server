package com.ditto.api.match

import com.ditto.api.match.service.SecretMatchingStateService
import com.ditto.api.support.IntegrationTest
import com.ditto.api.system.featureflag.FeatureFlagAdminService
import com.ditto.api.system.featureflag.FeatureFlags
import com.ditto.domain.match.GroupMatchFixture
import com.ditto.domain.match.MatchCandidateFixture
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.entity.CandidateOrigin
import com.ditto.domain.match.entity.GroupMatchMember
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.quiz.QuizProgressFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.sanction.SanctionFixture
import com.ditto.domain.sanction.entity.SanctionLevel
import com.ditto.domain.sanction.repository.SanctionRepository
import com.ditto.domain.system.entity.FeatureFlagKey
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource

class SecretMatchingStateServiceTest(
    private val secretMatchingStateService: SecretMatchingStateService,
    private val quizSetRepository: QuizSetRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val personalMatchRepository: PersonalMatchRepository,
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    private val matchCandidateRepository: MatchCandidateRepository,
    private val sanctionRepository: SanctionRepository,
    private val featureFlags: FeatureFlags,
    private val featureFlagAdminService: FeatureFlagAdminService,
    dataSource: DataSource,
) : IntegrationTest(
    dataSource,
    {
        beforeTest { featureFlags.evict() }
        afterTest { featureFlags.evict() }

        // 2026-11-16(월) 시작 주. 응답 마감 수 23:59:59, 그룹 응답 마감 금 00:00, 라운드 금·토 20:00.
        val monday = LocalDate.of(2026, 11, 16)
        val thursday = monday.plusDays(3).atTime(10, 0)
        val friday = monday.plusDays(4).atTime(10, 0)
        val firstRound = monday.plusDays(4).atTime(20, 0)
        val memberId = 1L

        fun enableSecretMatching() =
            featureFlagAdminService.set(FeatureFlagKey.SECRET_MATCHING, enabled = true, adminMemberId = 1L)

        fun saveWeekQuizSet(matchingType: MatchingType = MatchingType.ONE_TO_ONE): Long =
            quizSetRepository.save(
                QuizSetFixture.create(
                    startDate = monday.atStartOfDay(),
                    endDate = monday.plusDays(2).atTime(23, 59, 59),
                    matchingType = matchingType,
                ),
            ).id

        fun complete(quizSetId: Long, completedAt: LocalDateTime? = null) {
            val progress = QuizProgressFixture.create(memberId = memberId, quizSetId = quizSetId, totalCount = 1)
            progress.recordAnswer(completedAt)
            quizProgressRepository.save(progress)
        }

        fun stateAt(now: LocalDateTime) = secretMatchingStateService.getSecretMatchingState(memberId, now)

        "플래그가 꺼져 있으면 NONE 이고 셋을 알려 주지 않는다" {
            saveWeekQuizSet()

            val response = stateAt(friday)

            response.state shouldBe SecretMatchingState.NONE
            response.quizSetId shouldBe null
            response.weekStartedOn shouldBe monday
        }

        "이번 주 1:1 셋이 없으면 NONE 이다" {
            enableSecretMatching()
            saveWeekQuizSet(MatchingType.GROUP)

            stateAt(friday).state shouldBe SecretMatchingState.NONE
        }

        "1:1 을 안 풀었으면 ELIGIBLE 이고 이번 주 1:1 셋과 다음 라운드 시각을 준다" {
            enableSecretMatching()
            val quizSetId = saveWeekQuizSet()

            val response = stateAt(thursday)

            response.state shouldBe SecretMatchingState.ELIGIBLE
            response.quizSetId shouldBe quizSetId
            response.nextRoundAt shouldBe firstRound
        }

        "늦게 완주했으면 자기 라운드까지 WAITING 이다" {
            enableSecretMatching()
            complete(saveWeekQuizSet(), completedAt = thursday)

            stateAt(friday).state shouldBe SecretMatchingState.WAITING
        }

        "1:1 성사나 경고 제재가 있으면 NONE 이다" - {
            "1:1 이 성사됐다" {
                enableSecretMatching()
                val quizSetId = saveWeekQuizSet()
                complete(quizSetId)
                personalMatchRepository.save(
                    PersonalMatchFixture.create(requesterId = memberId, receiverId = 2L, quizSetId = quizSetId, status = PersonalMatchStatus.ACCEPTED),
                )

                stateAt(friday).state shouldBe SecretMatchingState.NONE
            }

            "경고 제재 중이다" {
                enableSecretMatching()
                saveWeekQuizSet()
                sanctionRepository.save(
                    SanctionFixture.create(memberId = memberId, level = SanctionLevel.WARNING, startsAt = thursday.minusDays(1)),
                )

                stateAt(friday).state shouldBe SecretMatchingState.NONE
            }
        }

        "정규 후보만 있으면 AUTO_ENTERED 다" {
            enableSecretMatching()
            val quizSetId = saveWeekQuizSet()
            complete(quizSetId)
            matchCandidateRepository.save(MatchCandidateFixture.create(ownerMemberId = memberId, otherMemberId = 2L, quizSetId = quizSetId))

            stateAt(friday).state shouldBe SecretMatchingState.AUTO_ENTERED
        }

        "시크릿 후보가 있으면 SECRET_RESULT 다" {
            enableSecretMatching()
            val quizSetId = saveWeekQuizSet()
            complete(quizSetId)
            matchCandidateRepository.save(
                MatchCandidateFixture.create(
                    ownerMemberId = memberId,
                    otherMemberId = 3L,
                    quizSetId = quizSetId,
                    candidateOrigin = CandidateOrigin.SECRET_FIRST,
                ),
            )

            stateAt(firstRound.plusMinutes(1)).state shouldBe SecretMatchingState.SECRET_RESULT
        }

        "1:1 을 안 푼 그룹 완주자" - {
            "셋에 후보 그룹이 아직 없으면 그룹 응답 마감 전에는 NONE 이다" {
                enableSecretMatching()
                saveWeekQuizSet()
                complete(saveWeekQuizSet(MatchingType.GROUP))

                stateAt(thursday).state shouldBe SecretMatchingState.NONE
            }

            "배치 뒤 후보 그룹을 받지 못했으면 목요일부터 ELIGIBLE 이다" {
                enableSecretMatching()
                saveWeekQuizSet()
                val groupQuizSetId = saveWeekQuizSet(MatchingType.GROUP)
                complete(groupQuizSetId)
                val otherGroup = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = groupQuizSetId))
                groupMatchMemberRepository.save(GroupMatchMember.candidate(otherGroup.id, memberId + 1))

                stateAt(thursday).state shouldBe SecretMatchingState.ELIGIBLE
            }

            "그룹 초대를 거절했으면 목요일부터 ELIGIBLE 이다" {
                enableSecretMatching()
                saveWeekQuizSet()
                val groupQuizSetId = saveWeekQuizSet(MatchingType.GROUP)
                complete(groupQuizSetId)
                val group = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = groupQuizSetId))
                groupMatchMemberRepository.save(GroupMatchMember.candidate(group.id, memberId).apply { decline() })

                stateAt(thursday).state shouldBe SecretMatchingState.ELIGIBLE
            }

            "그룹 응답 마감부터 ELIGIBLE 이다" {
                enableSecretMatching()
                saveWeekQuizSet()
                complete(saveWeekQuizSet(MatchingType.GROUP))

                stateAt(friday).state shouldBe SecretMatchingState.ELIGIBLE
            }

            "성사된 그룹에 수락했으면 NONE 이다" {
                enableSecretMatching()
                saveWeekQuizSet()
                val groupQuizSetId = saveWeekQuizSet(MatchingType.GROUP)
                complete(groupQuizSetId)
                val formedGroup = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = groupQuizSetId, acceptedCount = 3))
                groupMatchMemberRepository.save(GroupMatchMember.candidate(formedGroup.id, memberId).apply { accept() })

                stateAt(friday).state shouldBe SecretMatchingState.NONE
            }
        }
    },
)
