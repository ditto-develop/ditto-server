package com.ditto.api.notification

import com.ditto.api.notification.notifier.SecretMatchReminderNotifier
import com.ditto.api.notification.notifier.SecretMatchReminderResult
import com.ditto.api.support.IntegrationTest
import com.ditto.api.system.featureflag.FeatureFlagAdminService
import com.ditto.api.system.featureflag.FeatureFlags
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.notification.repository.NotificationRepository
import com.ditto.domain.quiz.QuizProgressFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QuizSet
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

class SecretMatchReminderNotifierTest(
    private val secretMatchReminderNotifier: SecretMatchReminderNotifier,
    private val memberRepository: MemberRepository,
    private val quizSetRepository: QuizSetRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val sanctionRepository: SanctionRepository,
    private val notificationRepository: NotificationRepository,
    private val featureFlags: FeatureFlags,
    private val featureFlagAdminService: FeatureFlagAdminService,
    dataSource: DataSource,
) : IntegrationTest(
    dataSource,
    {
        beforeTest { featureFlags.evict() }
        afterTest { featureFlags.evict() }

        // 2026-11-16(월) 시작 주. 응답 마감 수 23:59:59, 안내는 목 12:00, 라운드는 금·토 20:00.
        val monday = LocalDate.of(2026, 11, 16)
        val wednesday = LocalDateTime.of(2026, 11, 18, 12, 0)
        val thursdayNoon = LocalDateTime.of(2026, 11, 19, 12, 0)
        val fridayRound = LocalDateTime.of(2026, 11, 20, 20, 0)
        val saturdayRound = LocalDateTime.of(2026, 11, 21, 20, 0)

        fun enableSecretMatching() =
            featureFlagAdminService.set(FeatureFlagKey.SECRET_MATCHING, enabled = true, adminMemberId = 1L)

        fun weekQuizSet(matchingType: MatchingType): QuizSet = quizSetRepository.save(
            QuizSetFixture.create(
                startDate = monday.atStartOfDay(),
                endDate = monday.plusDays(2).atTime(23, 59, 59),
                matchingType = matchingType,
            ),
        )

        fun activeMember(nickname: String): Member =
            memberRepository.save(MemberFixture.create(nickname = nickname, email = "$nickname@ditto.pics", status = MemberStatus.ACTIVE))

        fun complete(memberId: Long, quizSetId: Long) {
            quizProgressRepository.save(
                QuizProgressFixture.create(memberId = memberId, quizSetId = quizSetId, totalCount = 1).apply { recordAnswer() },
            )
        }

        fun remindedMemberIds() = notificationRepository.findAll()
            .filter { it.type == NotificationType.SECRET_MATCH_REMINDER }
            .map { it.memberId }
            .toSet()

        "이번 주 퀴즈를 하나도 끝내지 않은 활성 회원에게만 금요일 라운드를 안내한다" {
            enableSecretMatching()
            val oneToOne = weekQuizSet(MatchingType.ONE_TO_ONE)
            val group = weekQuizSet(MatchingType.GROUP)
            val nonParticipant = activeMember("미참여")
            activeMember("1on1완주").also { complete(it.id, oneToOne.id) }
            activeMember("그룹완주").also { complete(it.id, group.id) }
            activeMember("경고").also {
                sanctionRepository.save(
                    SanctionFixture.create(memberId = it.id, level = SanctionLevel.WARNING, startsAt = monday.atStartOfDay()),
                )
            }
            memberRepository.save(MemberFixture.create(nickname = "탈퇴", email = "left@ditto.pics", status = MemberStatus.LEFT))

            val result = secretMatchReminderNotifier.notifyNonParticipants(thursdayNoon)

            result shouldBe SecretMatchReminderResult(SecretMatchReminderResult.Status.SENT, 1, fridayRound)
            remindedMemberIds() shouldBe setOf(nonParticipant.id)
            notificationRepository.findAll().single().let {
                it.title shouldBe "시크릿 매칭 기회가 있어요"
                it.body shouldBe "지금 1:1 퀴즈를 풀면 금요일 저녁 8시에 같은 답을 한 사람을 찾아드려요."
                it.targetId shouldBe oneToOne.id
            }
        }

        "이미 받은 회원에게는 다시 보내지 않는다" {
            enableSecretMatching()
            weekQuizSet(MatchingType.ONE_TO_ONE)
            activeMember("미참여")
            secretMatchReminderNotifier.notifyNonParticipants(thursdayNoon).sentCount shouldBe 1

            secretMatchReminderNotifier.notifyNonParticipants(thursdayNoon.plusHours(1)).status shouldBe
                SecretMatchReminderResult.Status.NO_RECIPIENT
        }

        "응답 마감 전에는 보내지 않는다" {
            enableSecretMatching()
            weekQuizSet(MatchingType.ONE_TO_ONE)
            activeMember("미참여")

            secretMatchReminderNotifier.notifyNonParticipants(wednesday).status shouldBe
                SecretMatchReminderResult.Status.RESPONSE_PERIOD
            notificationRepository.count() shouldBe 0
        }

        "기능이 꺼져 있으면 보내지 않는다" {
            weekQuizSet(MatchingType.ONE_TO_ONE)
            activeMember("미참여")

            secretMatchReminderNotifier.notifyNonParticipants(thursdayNoon).status shouldBe
                SecretMatchReminderResult.Status.FLAG_OFF
        }

        "그 주 1:1 셋이 없으면 보내지 않는다" {
            enableSecretMatching()
            weekQuizSet(MatchingType.GROUP)
            activeMember("미참여")

            secretMatchReminderNotifier.notifyNonParticipants(thursdayNoon).status shouldBe
                SecretMatchReminderResult.Status.NOT_ANNOUNCEABLE
        }

        "토 20:00 라운드가 시작되면 남은 라운드가 없어 보내지 않는다" {
            enableSecretMatching()
            weekQuizSet(MatchingType.ONE_TO_ONE)
            activeMember("미참여")

            secretMatchReminderNotifier.notifyNonParticipants(saturdayRound).status shouldBe
                SecretMatchReminderResult.Status.NOT_ANNOUNCEABLE
        }
    },
)
