package com.ditto.api.notification

import com.ditto.api.notification.notifier.ChatVoteNotifier
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.chat.ChatRoomMemberFixture
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.notification.repository.NotificationRepository
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

private const val ROOM = 1L

class ChatVoteNotifierTest(
    private val chatVoteNotifier: ChatVoteNotifier,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val notificationRepository: NotificationRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "투표가 시작되면 방 멤버에게 알린다" - {
        "생성자 본인은 받지 않는다 — 자기가 만들었다" {
            listOf(1L, 2L, 3L).forEach {
                chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = ROOM, memberId = it))
            }

            chatVoteNotifier.notifyCreated(ROOM, createdBy = 2L) shouldBe 2

            val notifications = notificationRepository.findAll()
            notifications.map { it.memberId }.toSet() shouldBe setOf(1L, 3L)
            notifications.first().let {
                it.type shouldBe NotificationType.VOTE_CREATED
                it.title shouldBe "만남 투표가 시작됐어요"
                it.body shouldBe "언제 어디서 만날지 정해요. 일요일 자정까지 투표할 수 있어요."
                it.targetId shouldBe ROOM
            }
        }

        // 마감 뒤 같은 방에서 다시 시작한 투표는 정당한 새 알림이다 — 중복을 유형이 막지 않는다
        "같은 방의 다음 투표 시작도 알린다" {
            listOf(1L, 2L).forEach {
                chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = ROOM, memberId = it))
            }
            chatVoteNotifier.notifyCreated(ROOM, createdBy = 1L)

            chatVoteNotifier.notifyCreated(ROOM, createdBy = 1L) shouldBe 1

            notificationRepository.count() shouldBe 2
        }
    }

    "투표가 마감되면 방 멤버에게 알린다" - {
        "마감자 본인은 받지 않는다 — 자기가 눌렀다" {
            listOf(1L, 2L, 3L).forEach {
                chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = ROOM, memberId = it))
            }

            chatVoteNotifier.notifyClosed(ROOM, closedBy = 1L) shouldBe 2

            val notifications = notificationRepository.findAll()
            notifications.map { it.memberId }.toSet() shouldBe setOf(2L, 3L)
            notifications.first().let {
                it.type shouldBe NotificationType.VOTE_CLOSED
                it.title shouldBe "만남 투표가 끝났어요"
                it.targetId shouldBe ROOM
            }
        }

        "방을 나간 멤버는 받지 않는다 — 투표 집계에서 빠지는 것과 같은 기준이다" {
            chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = ROOM, memberId = 1L))
            chatRoomMemberRepository.save(
                ChatRoomMemberFixture.create(roomId = ROOM, memberId = 2L)
                    .apply { leave(LocalDateTime.of(2026, 3, 14, 10, 0)) },
            )
            chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = ROOM, memberId = 3L))

            chatVoteNotifier.notifyClosed(ROOM, closedBy = 3L) shouldBe 1

            notificationRepository.findAll().single().memberId shouldBe 1L
        }
    }
})
