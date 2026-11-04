package com.ditto.domain.chat.repository

import com.ditto.domain.chat.ChatMessageFixture
import com.ditto.domain.chat.ChatRoomFixture
import com.ditto.domain.chat.entity.ChatMessage
import com.ditto.domain.chat.entity.ChatPeriod
import com.ditto.domain.chat.entity.ChatQuizHint
import com.ditto.domain.chat.entity.ChatRoomStatus
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

// 기본 픽스처 방: 2026-03-13(금) 00:00 개방, 금요일 정오에 만들어 ACTIVE.
private val OPENS_AT = LocalDateTime.of(2026, 3, 13, 0, 0)

class ChatRoomHintQueryTest(
    private val chatRoomRepository: ChatRoomRepository,
    private val chatQuizHintRepository: ChatQuizHintRepository,
    private val chatMessageRepository: ChatMessageRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "findAllIdsActiveWithoutHint — 첫 대화 힌트 후보" - {
        "열려 있고 힌트 표시가 없는 방은 나온다" {
            val room = chatRoomRepository.save(ChatRoomFixture.personal())

            chatRoomRepository.findAllIdsActiveWithoutHint() shouldBe listOf(room.id)
        }

        "힌트 표시가 있는 방은 — 빈 표시라도 — 나오지 않는다" {
            val room = chatRoomRepository.save(ChatRoomFixture.personal())
            chatQuizHintRepository.save(ChatQuizHint.of(room.id, messageId = null))

            chatRoomRepository.findAllIdsActiveWithoutHint().shouldBeEmpty()
        }

        "아직 열리지 않은 방은 나오지 않는다" {
            val thursday = LocalDateTime.of(2026, 3, 12, 12, 0)
            val room = chatRoomRepository.save(ChatRoomFixture.personal(now = thursday, period = ChatPeriod.weekendOf(thursday)))
            room.status shouldBe ChatRoomStatus.SCHEDULED

            chatRoomRepository.findAllIdsActiveWithoutHint().shouldBeEmpty()
        }
    }

    "첫 대화 힌트는 대화 메시지로 세지 않는다 — 힌트만 있는 방도 첫 메시지 리마인드 대상이다" {
        val room = chatRoomRepository.save(ChatRoomFixture.personal())
        chatMessageRepository.save(ChatMessage.quizHint(room.id, "{}"))

        chatRoomRepository.findAllIdsSilentOpenedBetween(OPENS_AT.minusHours(6), OPENS_AT) shouldBe listOf(room.id)
    }

    "방 목록 안 읽음 수는 첫 대화 힌트를 빼고 센다" {
        val room = chatRoomRepository.save(ChatRoomFixture.personal())
        val hint = chatMessageRepository.save(ChatMessage.quizHint(room.id, "{}"))
        chatMessageRepository.save(ChatMessageFixture.create(roomId = room.id))

        chatMessageRepository.countByRoomIdAndMessageTypeNot(room.id, hint.messageType) shouldBe 1L
        chatMessageRepository.countByRoomIdAndIdGreaterThanAndMessageTypeNot(room.id, 0L, hint.messageType) shouldBe 1L
    }
})
