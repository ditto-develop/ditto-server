package com.ditto.domain.chat.repository

import com.ditto.domain.chat.entity.ChatQuizCard
import com.ditto.domain.chat.entity.ChatQuizHint
import com.ditto.domain.support.IntegrationTest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.springframework.dao.DataIntegrityViolationException
import javax.sql.DataSource

class ChatQuizCardRepositoryTest(
    private val chatQuizCardRepository: ChatQuizCardRepository,
    private val chatQuizHintRepository: ChatQuizHintRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "올린 카드를 방·문항으로 찾는다" {
        chatQuizCardRepository.save(ChatQuizCard.of(roomId = 1L, quizId = 10L, messageId = 100L, senderId = 7L))

        chatQuizCardRepository.existsByRoomIdAndQuizId(1L, 10L) shouldBe true
        chatQuizCardRepository.existsByRoomIdAndQuizId(1L, 11L) shouldBe false
        chatQuizCardRepository.findByRoomId(1L).single().let {
            it.messageId shouldBe 100L
            it.senderId shouldBe 7L
        }
        chatQuizCardRepository.findByRoomIdIn(listOf(1L, 2L)).size shouldBe 1
    }

    "같은 방에 같은 문항 카드는 두 번 저장되지 않는다" {
        chatQuizCardRepository.saveAndFlush(ChatQuizCard.of(roomId = 1L, quizId = 10L, messageId = 100L, senderId = 7L))

        shouldThrow<DataIntegrityViolationException> {
            chatQuizCardRepository.saveAndFlush(ChatQuizCard.of(roomId = 1L, quizId = 10L, messageId = 101L, senderId = 8L))
        }
    }

    "방의 첫 대화 힌트 표시는 하나뿐이다" {
        chatQuizHintRepository.saveAndFlush(ChatQuizHint.of(roomId = 1L, messageId = 100L))

        chatQuizHintRepository.findByRoomIdIn(listOf(1L)).single().messageId shouldBe 100L
        shouldThrow<DataIntegrityViolationException> {
            chatQuizHintRepository.saveAndFlush(ChatQuizHint.of(roomId = 1L, messageId = null))
        }
    }
})
