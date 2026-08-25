package com.ditto.api.notification

import com.ditto.api.notification.notifier.ChatEndingSoonNotifier
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

// 금요일 정오에 만든 방은 ACTIVE 이고 월요일 00:00 에 끝난다.
private val FRIDAY_NOON = ChatRoomFixture.DEFAULT_NOW

/** 종료(월 00:00)까지 4시간 남은 시점 — 6시간 창 안이다. */
private val FOUR_HOURS_BEFORE_END = LocalDateTime.of(2026, 3, 15, 20, 0)

class ChatEndingSoonNotifierTest(
    private val chatEndingSoonNotifier: ChatEndingSoonNotifier,
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val notificationRepository: NotificationRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "종료가 가까운 방의 참여자에게 알린다" - {
        "참여자 전원이 받는다" {
            val room = chatRoomRepository.save(ChatRoomFixture.personal(now = FRIDAY_NOON))
            listOf(1L, 2L).forEach {
                chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = room.id, memberId = it))
            }

            chatEndingSoonNotifier.notifyEndingSoon(FOUR_HOURS_BEFORE_END) shouldBe 2

            val notification = notificationRepository.findAll().first()
            notification.type shouldBe NotificationType.CHAT_ENDING_SOON
            notification.title shouldBe "채팅이 6시간 후 종료돼요"
            notification.targetId shouldBe room.id
        }

        "매 주기 다시 집어와도 방마다 한 번만 알린다 — 알림 행 자체가 처리 완료 표시다" {
            val room = chatRoomRepository.save(ChatRoomFixture.personal(now = FRIDAY_NOON))
            chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = room.id, memberId = 1L))
            chatEndingSoonNotifier.notifyEndingSoon(FOUR_HOURS_BEFORE_END)

            chatEndingSoonNotifier.notifyEndingSoon(FOUR_HOURS_BEFORE_END.plusMinutes(1)) shouldBe 0

            notificationRepository.count() shouldBe 1
        }

        "방을 나간 멤버에게는 알리지 않는다 — 종료 임박이 이탈자에게는 의미가 없다" {
            val room = chatRoomRepository.save(ChatRoomFixture.group(now = FRIDAY_NOON))
            chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = room.id, memberId = 1L))
            chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = room.id, memberId = 2L))
            chatRoomMemberRepository.save(
                ChatRoomMemberFixture.create(roomId = room.id, memberId = 3L)
                    .apply { leave(LocalDateTime.of(2026, 3, 14, 10, 0)) },
            )

            chatEndingSoonNotifier.notifyEndingSoon(FOUR_HOURS_BEFORE_END) shouldBe 2

            notificationRepository.findAll().map { it.memberId }.toSet() shouldBe setOf(1L, 2L)
        }

        "아직 창 밖이면 알리지 않는다" {
            val room = chatRoomRepository.save(ChatRoomFixture.personal(now = FRIDAY_NOON))
            chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = room.id, memberId = 1L))

            chatEndingSoonNotifier.notifyEndingSoon(FRIDAY_NOON) shouldBe 0

            notificationRepository.count() shouldBe 0
        }
    }
})
