package com.ditto.api.notification

import com.ditto.api.notification.notifier.MatchResultNotifier
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.match.MatchCandidateFixture
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.notification.repository.NotificationRepository
import io.kotest.matchers.shouldBe
import javax.sql.DataSource

private const val QUIZ_SET = 1L
private const val MEMBER_A = 1L
private const val MEMBER_B = 2L

class MatchResultNotifierTest(
    private val matchResultNotifier: MatchResultNotifier,
    private val matchCandidateRepository: MatchCandidateRepository,
    private val notificationRepository: NotificationRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun saveCandidatePair(quizSetId: Long = QUIZ_SET) {
        // 후보는 (A→B), (B→A) 두 방향 행으로 저장된다.
        matchCandidateRepository.save(
            MatchCandidateFixture.create(ownerMemberId = MEMBER_A, otherMemberId = MEMBER_B, quizSetId = quizSetId),
        )
        matchCandidateRepository.save(
            MatchCandidateFixture.create(ownerMemberId = MEMBER_B, otherMemberId = MEMBER_A, quizSetId = quizSetId),
        )
    }

    "후보가 생긴 회원에게 알린다" - {
        "쌍의 양쪽 모두 알림을 받는다" {
            saveCandidatePair()

            matchResultNotifier.notifyFor(listOf(QUIZ_SET)) shouldBe 2

            val notifications = notificationRepository.findAll()
            notifications.map { it.memberId }.toSet() shouldBe setOf(MEMBER_A, MEMBER_B)
            notifications.first().type shouldBe NotificationType.MATCH_RESULT
            notifications.first().title shouldBe "이번 주 매칭 결과가 나왔어요"
            // 대상은 퀴즈셋이다 — "주마다 한 번"을 판정하는 기준이다.
            notifications.first().targetId shouldBe QUIZ_SET
        }

        "다시 불러도 중복으로 남지 않는다 — 어드민이 후보를 재생성할 수 있다" {
            saveCandidatePair()
            matchResultNotifier.notifyFor(listOf(QUIZ_SET))

            matchResultNotifier.notifyFor(listOf(QUIZ_SET)) shouldBe 0

            notificationRepository.count() shouldBe 2
        }

        "후보가 없는 퀴즈셋에는 알리지 않는다 — 빈 화면으로 보내지 않는다" {
            matchResultNotifier.notifyFor(listOf(QUIZ_SET)) shouldBe 0

            notificationRepository.count() shouldBe 0
        }

        "대상 퀴즈셋이 없으면 아무 것도 하지 않는다" {
            matchResultNotifier.notifyFor(emptyList()) shouldBe 0
        }
    }
})
