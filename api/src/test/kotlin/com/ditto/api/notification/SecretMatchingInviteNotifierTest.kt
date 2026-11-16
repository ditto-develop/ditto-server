package com.ditto.api.notification

import com.ditto.api.notification.notifier.SecretMatchingInviteNotifier
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

class SecretMatchingInviteNotifierTest(
    private val secretMatchingInviteNotifier: SecretMatchingInviteNotifier,
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

        // 2026-11-16(월) 시작 주. 안내는 목 12:00, 1차 라운드는 금 20:00.
        val monday = LocalDate.of(2026, 11, 16)
        val thursdayNoon = LocalDateTime.of(2026, 11, 19, 12, 0)

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

        fun invitedMemberIds() = notificationRepository.findAll()
            .filter { it.type == NotificationType.SECRET_MATCHING_INVITED }
            .map { it.memberId }
            .toSet()

        "이번 주 퀴즈를 하나도 끝내지 않은 활성 회원에게만 금요일 라운드를 안내한다" {
            enableSecretMatching()
            val oneToOne = weekQuizSet(MatchingType.ONE_TO_ONE)
            val group = weekQuizSet(MatchingType.GROUP)
            val notParticipated = activeMember("미참여")
            val oneToOneDone = activeMember("1on1완주").also { complete(it.id, oneToOne.id) }
            val groupDone = activeMember("그룹완주").also { complete(it.id, group.id) }
            val warned = activeMember("경고").also {
                sanctionRepository.save(
                    SanctionFixture.create(memberId = it.id, level = SanctionLevel.WARNING, startsAt = monday.atStartOfDay()),
                )
            }
            memberRepository.save(MemberFixture.create(nickname = "탈퇴", email = "left@ditto.pics", status = MemberStatus.LEFT))

            secretMatchingInviteNotifier.notifyNotParticipated(thursdayNoon) shouldBe 1

            invitedMemberIds() shouldBe setOf(notParticipated.id)
            notificationRepository.findAll().single().let {
                it.title shouldBe "시크릿 매칭 기회가 있어요"
                it.body shouldBe "지금 1:1 퀴즈를 풀면 금요일 저녁 8시에 같은 답을 한 사람을 찾아드려요."
                it.targetId shouldBe oneToOne.id
            }
            listOf(oneToOneDone, groupDone, warned).forEach { (it.id in invitedMemberIds()) shouldBe false }
        }

        "주마다 한 번만 보낸다" {
            enableSecretMatching()
            weekQuizSet(MatchingType.ONE_TO_ONE)
            activeMember("미참여")
            secretMatchingInviteNotifier.notifyNotParticipated(thursdayNoon) shouldBe 1

            secretMatchingInviteNotifier.notifyNotParticipated(thursdayNoon.plusHours(1)) shouldBe 0
        }

        "기능이 꺼져 있거나 그 주 1:1 셋이 없거나 남은 라운드가 없으면 보내지 않는다" {
            activeMember("미참여")
            weekQuizSet(MatchingType.GROUP)
            secretMatchingInviteNotifier.notifyNotParticipated(thursdayNoon) shouldBe 0

            enableSecretMatching()
            secretMatchingInviteNotifier.notifyNotParticipated(thursdayNoon) shouldBe 0

            weekQuizSet(MatchingType.ONE_TO_ONE)
            secretMatchingInviteNotifier.notifyNotParticipated(LocalDateTime.of(2026, 11, 21, 20, 0)) shouldBe 0
            notificationRepository.count() shouldBe 0
        }
    },
)
