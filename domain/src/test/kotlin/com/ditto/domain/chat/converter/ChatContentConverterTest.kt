package com.ditto.domain.chat.converter

import com.ditto.domain.chat.ChatMessageFixture
import com.ditto.domain.chat.entity.ChatMessage
import com.ditto.domain.chat.repository.ChatMessageRepository
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

class ChatContentConverterTest(
    private val chatMessageRepository: ChatMessageRepository,
    private val chatContentCipher: ChatContentCipher,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val jdbcTemplate = JdbcTemplate(dataSource)

    fun storedContentOf(messageId: Long): String =
        jdbcTemplate.queryForObject("SELECT content FROM chat_message WHERE id = ?", String::class.java, messageId)
            ?: error("메시지가 없다: $messageId")

    "새 메시지는 암호화해 저장하고 읽을 때 원문을 돌려준다" {
        val saved = chatMessageRepository.save(ChatMessageFixture.create(content = "안녕하세요"))

        storedContentOf(saved.id) shouldStartWith ChatContentCipher.ENCRYPTED_PREFIX
        storedContentOf(saved.id) shouldNotContain "안녕하세요"
        chatMessageRepository.findById(saved.id).get().content shouldBe "안녕하세요"
    }

    "암호화 전에 쌓인 평문 행은 그대로 읽는다" {
        val legacyRoomId = 1L
        jdbcTemplate.update(
            "INSERT INTO chat_message (room_id, sender_id, message_type, content, created_at, updated_at) " +
                "VALUES (?, 1, 'TEXT', '예전 메시지', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            legacyRoomId,
        )

        chatMessageRepository.findByRoomIdWithCursor(roomId = legacyRoomId, cursor = null, size = 10)
            .map { it.content } shouldBe listOf("예전 메시지")
    }

    "상한까지 한글로 채운 본문도 컬럼에 들어간다" {
        val longest = "가".repeat(ChatMessage.MAX_CONTENT_LENGTH)

        chatContentCipher.encrypt(longest).length shouldBeLessThanOrEqual ChatMessage.STORED_CONTENT_LENGTH

        val saved = chatMessageRepository.save(ChatMessageFixture.create(content = longest))
        chatMessageRepository.findById(saved.id).get().content shouldBe longest
    }
})
