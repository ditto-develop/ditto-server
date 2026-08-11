package com.ditto.api.notification

import com.ditto.api.notification.message.NotificationContent
import com.ditto.api.notification.message.NotificationMessages
import com.ditto.api.notification.service.NotificationAppender
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.notification.repository.NotificationRepository
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

private const val ME = 1L
private const val ROOM = 100L
private const val QUIZ_SET = 7L

class NotificationAppenderTest(
    private val notificationAppender: NotificationAppender,
    private val notificationRepository: NotificationRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "한 번만 알리는 유형 (ONCE_PER_TARGET)" - {
        "같은 대상에 두 번 부르면 행은 하나다 — 스케줄러가 매 주기 다시 집어와도 알림은 하나여야 한다" {
            notificationAppender.append(ME, NotificationMessages.matchResult(), targetId = QUIZ_SET) shouldBe true
            notificationAppender.append(ME, NotificationMessages.matchResult(), targetId = QUIZ_SET) shouldBe false

            notificationRepository.count() shouldBe 1
        }

        "대상이 다르면 따로 남는다 — 다음 주 매칭은 다시 알려야 한다" {
            notificationAppender.append(ME, NotificationMessages.matchResult(), targetId = QUIZ_SET)
            notificationAppender.append(ME, NotificationMessages.matchResult(), targetId = QUIZ_SET + 1)

            notificationRepository.count() shouldBe 2
        }
    }

    "새 메시지는 방 단위로 접힌다 (COLLAPSE_UNREAD)" - {
        "같은 방의 안읽은 알림은 최신 한 줄만 남는다" {
            notificationAppender.append(ME, chatMessage("첫 메시지"), targetId = ROOM)
            notificationAppender.append(ME, chatMessage("두 번째 메시지"), targetId = ROOM)

            val remaining = notificationRepository.findAll()
            remaining.size shouldBe 1
            remaining.single().body shouldBe "두 번째 메시지"
        }

        // 접기는 안읽은 행만 걷어낸다. 읽은 알림은 "그때 이런 알림을 받았다"는 기록이다.
        "읽은 알림은 접히지 않고 남는다" {
            notificationAppender.append(ME, chatMessage("읽을 메시지"), targetId = ROOM)
            val read = notificationRepository.findAll().single()
            read.markRead(LocalDateTime.now())
            notificationRepository.save(read)

            notificationAppender.append(ME, chatMessage("새 메시지"), targetId = ROOM)

            notificationRepository.count() shouldBe 2
        }

        "다른 방의 알림은 접지 않는다" {
            notificationAppender.append(ME, chatMessage("A방"), targetId = ROOM)
            notificationAppender.append(ME, chatMessage("B방"), targetId = ROOM + 1)

            notificationRepository.count() shouldBe 2
        }

        // 접기는 갱신이 아니라 삭제+재삽입이다 — id 정렬이 곧 시간 정렬이어야 커서 페이징이 단순하다.
        "접힌 알림은 새 id 를 받는다" {
            notificationAppender.append(ME, chatMessage("첫 메시지"), targetId = ROOM)
            val firstId = notificationRepository.findAll().single().id

            notificationAppender.append(ME, chatMessage("두 번째 메시지"), targetId = ROOM)

            (notificationRepository.findAll().single().id > firstId) shouldBe true
        }
    }

    "여러 수신자" - {
        "사람마다 행이 하나씩 생긴다" {
            val appended = notificationAppender.appendAll(
                memberIds = listOf(1L, 2L, 3L),
                content = NotificationMessages.groupFormed(3),
                targetId = ROOM,
            )

            appended shouldBe 3
            notificationRepository.count() shouldBe 3
        }

        "이미 알린 사람은 세지 않는다" {
            notificationAppender.append(1L, NotificationMessages.groupFormed(3), targetId = ROOM)

            notificationAppender.appendAll(listOf(1L, 2L), NotificationMessages.groupFormed(3), ROOM) shouldBe 1
        }
    }

    "적재 실패는 삼킨다 — 알림 때문에 비즈니스 흐름이 끊기지 않는다" - {
        "대상이 필요한 유형에 targetId 가 없으면 예외 대신 false 를 돌려준다" {
            notificationAppender.append(ME, NotificationMessages.matchResult(), targetId = null) shouldBe false

            notificationRepository.count() shouldBe 0
        }
    }
})

private fun chatMessage(preview: String): NotificationContent = NotificationContent(
    type = NotificationType.CHAT_MESSAGE,
    title = "산책러버님의 새 메시지",
    body = preview,
)
