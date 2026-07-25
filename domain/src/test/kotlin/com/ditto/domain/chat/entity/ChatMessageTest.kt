package com.ditto.domain.chat.entity

import com.ditto.domain.chat.repository.ChatMessageRepository
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import javax.sql.DataSource

class ChatMessageTest(
    private val chatMessageRepository: ChatMessageRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "ChatMessage 생성" - {
        "when: of 로 만들면(타입 미지정)" - {
            "then: 기본 타입 TEXT 로 필드가 저장된다" {
                val message = chatMessageRepository.save(
                    ChatMessage.of(roomId = 1L, senderId = 2L, content = "안녕하세요"),
                )

                message.id shouldNotBe 0L
                message.roomId shouldBe 1L
                message.senderId shouldBe 2L
                message.content shouldBe "안녕하세요"
                message.messageType shouldBe ChatMessageType.TEXT
            }
        }

        "when: messageType 을 지정하면" - {
            "then: 지정한 타입으로 저장된다" {
                val message = chatMessageRepository.save(
                    ChatMessage.of(roomId = 1L, senderId = 2L, content = "img", messageType = ChatMessageType.IMAGE),
                )

                message.messageType shouldBe ChatMessageType.IMAGE
            }
        }
    }
})
