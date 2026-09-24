package com.ditto.api.notification

import com.ditto.api.notification.notifier.ChatNoMessageNotifier
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.chat.ChatMessageFixture
import com.ditto.domain.chat.ChatRoomFixture
import com.ditto.domain.chat.ChatRoomMemberFixture
import com.ditto.domain.chat.repository.ChatMessageRepository
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.notification.repository.NotificationRepository
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

// 금요일 정오에 만든 방은 ACTIVE 이고 금요일 00:00 에 열렸다. 기본 리드 12시간이면 정오부터 후보다.
private val FRIDAY_NOON = ChatRoomFixture.DEFAULT_NOW
private val FRIDAY_AFTERNOON = LocalDateTime.of(2026, 3, 13, 14, 0)

class ChatNoMessageNotifierTest(
    private val chatNoMessageNotifier: ChatNoMessageNotifier,
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val notificationRepository: NotificationRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "열린 뒤 아무도 말하지 않은 방의 참여자에게 알린다" - {
        "참여자 전원이 받는다" {
            val room = chatRoomRepository.save(ChatRoomFixture.personal(now = FRIDAY_NOON))
            listOf(1L, 2L).forEach {
                chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = room.id, memberId = it))
            }

            chatNoMessageNotifier.notifyNoMessage(FRIDAY_AFTERNOON) shouldBe 2

            val notification = notificationRepository.findAll().first()
            notification.type shouldBe NotificationType.CHAT_NO_MESSAGE
            notification.title shouldBe "아직 대화가 시작되지 않았어요"
            notification.body shouldBe "먼저 가벼운 인사부터 건네볼까요?"
            notification.targetId shouldBe room.id
        }

        "한쪽이라도 메시지를 보냈으면 알리지 않는다" {
            val room = chatRoomRepository.save(ChatRoomFixture.personal(now = FRIDAY_NOON))
            listOf(1L, 2L).forEach {
                chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = room.id, memberId = it))
            }
            chatMessageRepository.save(ChatMessageFixture.create(roomId = room.id, senderId = 1L))

            chatNoMessageNotifier.notifyNoMessage(FRIDAY_AFTERNOON) shouldBe 0
        }

        "매 주기 다시 집어와도 방마다 한 번만 알린다" {
            val room = chatRoomRepository.save(ChatRoomFixture.group(now = FRIDAY_NOON))
            chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = room.id, memberId = 1L))
            chatNoMessageNotifier.notifyNoMessage(FRIDAY_AFTERNOON)

            chatNoMessageNotifier.notifyNoMessage(FRIDAY_AFTERNOON.plusMinutes(1)) shouldBe 0

            notificationRepository.count() shouldBe 1
        }

        "방을 나간 멤버에게는 알리지 않는다" {
            val room = chatRoomRepository.save(ChatRoomFixture.group(now = FRIDAY_NOON))
            chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = room.id, memberId = 1L))
            chatRoomMemberRepository.save(
                ChatRoomMemberFixture.create(roomId = room.id, memberId = 2L)
                    .apply { leave(LocalDateTime.of(2026, 3, 13, 10, 0)) },
            )

            chatNoMessageNotifier.notifyNoMessage(FRIDAY_AFTERNOON) shouldBe 1

            notificationRepository.findAll().single().memberId shouldBe 1L
        }

        "열린 지 12시간이 안 됐으면 알리지 않는다" {
            val room = chatRoomRepository.save(ChatRoomFixture.personal(now = FRIDAY_NOON))
            chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = room.id, memberId = 1L))

            chatNoMessageNotifier.notifyNoMessage(FRIDAY_NOON.minusHours(2)) shouldBe 0
        }
    }
})
