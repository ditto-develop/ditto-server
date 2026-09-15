package com.ditto.domain.chat.entity

import com.ditto.domain.chat.ChatMessageFixture
import com.ditto.domain.chat.ChatRoomMemberFixture
import com.ditto.domain.chat.repository.ChatMessageRepository
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDateTime
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

    "unreadCountAmong — 메시지별 안읽음 수" - {
        fun member(memberId: Long, readUpTo: Long? = null, left: Boolean = false) =
            ChatRoomMemberFixture.create(roomId = 1L, memberId = memberId).apply {
                readUpTo?.let { this.readUpTo(it) }
                if (left) leave(LocalDateTime.of(2026, 3, 13, 12, 0))
            }

        "발신자는 세지 않는다 — 1:1 방에서 상대가 안 읽었으면 1" {
            val message = ChatMessageFixture.create(senderId = 1L, id = 10L)

            message.unreadCountAmong(listOf(member(1L), member(2L))) shouldBe 1
        }

        "커서가 메시지 id 이상인 참여자는 읽은 것이다" {
            val message = ChatMessageFixture.create(senderId = 1L, id = 10L)

            message.unreadCountAmong(listOf(member(1L), member(2L, readUpTo = 10L))) shouldBe 0
            message.unreadCountAmong(listOf(member(1L), member(2L, readUpTo = 9L))) shouldBe 1
        }

        "그룹 방은 안 읽은 참여자 수를 그대로 낸다" {
            val message = ChatMessageFixture.create(senderId = 1L, id = 10L)
            val members = listOf(member(1L), member(2L, readUpTo = 10L), member(3L, readUpTo = 3L), member(4L))

            message.unreadCountAmong(members) shouldBe 2
        }

        "이탈자는 분모에서 뺀다 — 안 빼면 나간 사람 때문에 숫자가 영영 줄지 않는다" {
            val message = ChatMessageFixture.create(senderId = 1L, id = 10L)
            val members = listOf(member(1L), member(2L, readUpTo = 10L), member(3L, left = true))

            message.unreadCountAmong(members) shouldBe 0
        }

        "SYSTEM 메시지는 읽음 표시 대상이 아니라 항상 0" {
            val message = ChatMessageFixture.create(senderId = 1L, id = 10L, messageType = ChatMessageType.SYSTEM)

            message.unreadCountAmong(listOf(member(1L), member(2L), member(3L))) shouldBe 0
        }
    }
})
