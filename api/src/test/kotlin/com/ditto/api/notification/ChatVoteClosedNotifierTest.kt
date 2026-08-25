package com.ditto.api.notification

import com.ditto.api.notification.notifier.ChatVoteClosedNotifier
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.chat.ChatRoomMemberFixture
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.notification.repository.NotificationRepository
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

private const val ROOM = 1L

class ChatVoteClosedNotifierTest(
    private val chatVoteClosedNotifier: ChatVoteClosedNotifier,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val notificationRepository: NotificationRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "투표가 마감되면 방 멤버에게 알린다" - {
        "마감자 본인은 받지 않는다 — 자기가 눌렀다" {
            listOf(1L, 2L, 3L).forEach {
                chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = ROOM, memberId = it))
            }

            chatVoteClosedNotifier.notifyClosed(ROOM, closedBy = 1L) shouldBe 2

            val notifications = notificationRepository.findAll()
            notifications.map { it.memberId }.toSet() shouldBe setOf(2L, 3L)
            notifications.first().let {
                it.type shouldBe NotificationType.VOTE_CLOSED
                it.title shouldBe "만남 투표가 마감됐어요"
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

            chatVoteClosedNotifier.notifyClosed(ROOM, closedBy = 3L) shouldBe 1

            notificationRepository.findAll().single().memberId shouldBe 1L
        }
    }
})
