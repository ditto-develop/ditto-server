package com.ditto.domain.chat.repository

import com.ditto.domain.chat.ChatMessageFixture
import com.ditto.domain.chat.ChatRoomFixture
import com.ditto.domain.chat.entity.ChatMessageType
import com.ditto.domain.chat.entity.ChatRoomStatus
import com.ditto.domain.notification.NotificationFixture
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.notification.repository.NotificationRepository
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

// 금요일 정오에 만든 방은 ACTIVE 이고 금요일 00:00 에 열렸다.
private val FRIDAY_NOON = ChatRoomFixture.DEFAULT_NOW
private val OPENS_AT = LocalDateTime.of(2026, 3, 13, 0, 0)

class ChatRoomSilentQueryTest(
    private val chatRoomRepository: ChatRoomRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val notificationRepository: NotificationRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "findAllIdsSilentOpenedBetween — 첫 메시지 리마인드 후보" - {
        "열린 시각이 창 안이고 메시지가 없으면 나온다" {
            val room = chatRoomRepository.save(ChatRoomFixture.personal(now = FRIDAY_NOON))
            room.status shouldBe ChatRoomStatus.ACTIVE
            room.opensAt shouldBe OPENS_AT

            chatRoomRepository.findAllIdsSilentOpenedBetween(OPENS_AT.minusHours(6), OPENS_AT) shouldBe listOf(room.id)
        }

        "대화 메시지가 하나라도 있으면 나오지 않는다" {
            val room = chatRoomRepository.save(ChatRoomFixture.personal(now = FRIDAY_NOON))
            chatMessageRepository.save(ChatMessageFixture.create(roomId = room.id, messageType = ChatMessageType.TEXT))

            chatRoomRepository.findAllIdsSilentOpenedBetween(OPENS_AT.minusHours(6), OPENS_AT).shouldBeEmpty()
        }

        "SYSTEM 메시지만 있으면 대화가 없는 것이다" {
            val room = chatRoomRepository.save(ChatRoomFixture.personal(now = FRIDAY_NOON))
            chatMessageRepository.save(ChatMessageFixture.create(roomId = room.id, messageType = ChatMessageType.SYSTEM))

            chatRoomRepository.findAllIdsSilentOpenedBetween(OPENS_AT.minusHours(6), OPENS_AT) shouldBe listOf(room.id)
        }

        "이미 리마인드를 남긴 방은 나오지 않는다" {
            val room = chatRoomRepository.save(ChatRoomFixture.personal(now = FRIDAY_NOON))
            notificationRepository.save(
                NotificationFixture.create(memberId = 1L, type = NotificationType.CHAT_NO_MESSAGE, targetId = room.id),
            )

            chatRoomRepository.findAllIdsSilentOpenedBetween(OPENS_AT.minusHours(6), OPENS_AT).shouldBeEmpty()
        }

        "다른 유형의 알림은 후보 판정에 영향이 없다" {
            val room = chatRoomRepository.save(ChatRoomFixture.personal(now = FRIDAY_NOON))
            notificationRepository.save(
                NotificationFixture.create(memberId = 1L, type = NotificationType.CHAT_ROOM_OPENED, targetId = room.id),
            )

            chatRoomRepository.findAllIdsSilentOpenedBetween(OPENS_AT.minusHours(6), OPENS_AT) shouldBe listOf(room.id)
        }

        "아직 열리지 않은 예약 방은 나오지 않는다" {
            val scheduled = chatRoomRepository.save(ChatRoomFixture.personal(now = FRIDAY_NOON.minusDays(1)))
            scheduled.status shouldBe ChatRoomStatus.SCHEDULED

            chatRoomRepository.findAllIdsSilentOpenedBetween(OPENS_AT.minusHours(6), OPENS_AT).shouldBeEmpty()
        }

        "열린 시각이 창 밖이면 나오지 않는다" {
            chatRoomRepository.save(ChatRoomFixture.personal(now = FRIDAY_NOON))

            chatRoomRepository.findAllIdsSilentOpenedBetween(OPENS_AT.minusHours(12), OPENS_AT.minusHours(6)).shouldBeEmpty()
        }
    }
})
