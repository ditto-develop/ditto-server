package com.ditto.api.notification

import com.ditto.api.notification.notifier.ChatRoomOpenedNotifier
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.chat.ChatRoomFixture
import com.ditto.domain.chat.ChatRoomMemberFixture
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.notification.repository.NotificationRepository
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

class ChatRoomOpenedNotifierTest(
    private val chatRoomOpenedNotifier: ChatRoomOpenedNotifier,
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val notificationRepository: NotificationRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "열린 방의 참여자에게 알린다" - {
        "참여자 전원이 받는다" {
            val room = chatRoomRepository.save(ChatRoomFixture.personal())
            listOf(1L, 2L).forEach {
                chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = room.id, memberId = it))
            }

            chatRoomOpenedNotifier.notifyOpened(listOf(room.id)) shouldBe 2

            val notification = notificationRepository.findAll().first()
            notification.type shouldBe NotificationType.CHAT_ROOM_OPENED
            notification.title shouldBe "이제 대화를 시작할 수 있어요"
            notification.body shouldBe "일요일 자정까지예요. 천천히 이야기 나눠보세요."
            notification.targetId shouldBe room.id
        }

        "같은 방을 다시 넘겨도 방마다 한 번만 알린다" {
            val room = chatRoomRepository.save(ChatRoomFixture.group())
            chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = room.id, memberId = 1L))
            chatRoomOpenedNotifier.notifyOpened(listOf(room.id))

            chatRoomOpenedNotifier.notifyOpened(listOf(room.id)) shouldBe 0

            notificationRepository.count() shouldBe 1
        }

        "열리기 전에 나간 멤버에게는 알리지 않는다" {
            val room = chatRoomRepository.save(ChatRoomFixture.group())
            chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = room.id, memberId = 1L))
            chatRoomMemberRepository.save(
                ChatRoomMemberFixture.create(roomId = room.id, memberId = 2L)
                    .apply { leave(LocalDateTime.of(2026, 3, 12, 10, 0)) },
            )

            chatRoomOpenedNotifier.notifyOpened(listOf(room.id)) shouldBe 1

            notificationRepository.findAll().single().memberId shouldBe 1L
        }

        "열린 방이 없으면 0 이다" {
            chatRoomOpenedNotifier.notifyOpened(emptyList()) shouldBe 0

            notificationRepository.count() shouldBe 0
        }
    }
})
