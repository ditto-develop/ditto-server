package com.ditto.domain.notification.entity

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

private val FIRST_READ = LocalDateTime.of(2026, 8, 11, 10, 0)
private val SECOND_READ = LocalDateTime.of(2026, 8, 11, 11, 0)

class NotificationTest : FreeSpec({

    "읽음 처리" - {
        "처음 읽으면 읽은 시각이 남는다" {
            val notification = notification()

            notification.markRead(FIRST_READ) shouldBe true

            notification.readAt shouldBe FIRST_READ
            notification.isRead shouldBe true
        }

        // 더블 탭·재시도가 처음 읽은 시각을 덮어쓰면 "언제 읽었는지"가 사라진다.
        "이미 읽었으면 시각을 덮어쓰지 않는다" {
            val notification = notification()
            notification.markRead(FIRST_READ)

            notification.markRead(SECOND_READ) shouldBe false

            notification.readAt shouldBe FIRST_READ
        }
    }

    "카테고리는 유형에서 파생된다 — 컬럼으로 저장하지 않아 어긋날 수 없다" {
        notification(type = NotificationType.CHAT_MESSAGE).category shouldBe NotificationCategory.CHAT
        notification(type = NotificationType.MATCH_RESULT).category shouldBe NotificationCategory.MATCHING
        notification(type = NotificationType.SYSTEM_NOTICE).category shouldBe NotificationCategory.SYSTEM
    }

    "긴 문구는 잘라서 저장한다 — 본문이 메시지 미리보기라 길이를 부르는 쪽이 통제할 수 없다" {
        val notification = Notification.create(
            memberId = 1L,
            type = NotificationType.CHAT_MESSAGE,
            title = "가".repeat(Notification.TITLE_MAX_LENGTH + 10),
            body = "나".repeat(Notification.BODY_MAX_LENGTH + 10),
            targetId = 1L,
        )

        notification.title.length shouldBe Notification.TITLE_MAX_LENGTH
        notification.title.last() shouldBe '…'
        notification.body!!.length shouldBe Notification.BODY_MAX_LENGTH
        notification.body!!.last() shouldBe '…'
    }

    "짧은 문구는 그대로 저장한다" {
        val notification = notification(title = "그룹이 구성됐어요")

        notification.title shouldBe "그룹이 구성됐어요"
    }

    "카테고리에 속한 유형을 모은다 — 목록 조회 필터가 쓴다" {
        NotificationType.of(NotificationCategory.CHAT) shouldBe
            listOf(NotificationType.CHAT_MESSAGE, NotificationType.CHAT_ENDING_SOON)
        NotificationType.of(NotificationCategory.SYSTEM) shouldBe listOf(NotificationType.SYSTEM_NOTICE)
    }

    "중복 정책은 대상 필요 여부를 스스로 답한다" {
        DuplicatePolicy.ALLOW.requiresTarget shouldBe false
        DuplicatePolicy.ONCE_PER_TARGET.requiresTarget shouldBe true
        DuplicatePolicy.COLLAPSE_UNREAD.requiresTarget shouldBe true
    }
})

private fun notification(
    type: NotificationType = NotificationType.MATCH_RESULT,
    title: String = "이번 주 매칭 결과가 나왔어요",
): Notification = Notification.create(
    memberId = 1L,
    type = type,
    title = title,
    body = "본문",
    targetId = 1L,
)
