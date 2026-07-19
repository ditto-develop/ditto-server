package com.ditto.domain.chat.repository

import com.ditto.domain.chat.entity.ChatMessage
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.shouldBe
import javax.sql.DataSource

class ChatMessageRepositoryTest(
    private val chatMessageRepository: ChatMessageRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "findByRoomIdWithCursor — 방 메시지 커서 페이징(최신순)" - {
        "given: 방에 메시지 5개가 있을 때" - {
            "when: cursor 없이 size=2 로 조회하면" - {
                "then: 최신순으로 2개를 반환한다" {
                    (1..5).forEach {
                        chatMessageRepository.save(ChatMessage.of(roomId = 1L, senderId = 1L, content = "m$it"))
                    }

                    val result = chatMessageRepository.findByRoomIdWithCursor(roomId = 1L, cursor = null, size = 2)

                    result.map { it.content } shouldBe listOf("m5", "m4")
                }
            }

            "when: cursor 를 주면" - {
                "then: 그 id 미만(더 과거)만 최신순으로 반환한다" {
                    val saved = (1..5).map {
                        chatMessageRepository.save(ChatMessage.of(roomId = 1L, senderId = 1L, content = "m$it"))
                    }

                    val result = chatMessageRepository.findByRoomIdWithCursor(roomId = 1L, cursor = saved[2].id, size = 10)

                    result.map { it.id } shouldBe listOf(saved[1].id, saved[0].id)
                }
            }
        }

        "given: 서로 다른 방의 메시지가 섞여 있을 때" - {
            "when: 특정 방으로 조회하면" - {
                "then: 그 방 메시지만 반환한다" {
                    chatMessageRepository.save(ChatMessage.of(roomId = 1L, senderId = 1L, content = "a"))
                    chatMessageRepository.save(ChatMessage.of(roomId = 2L, senderId = 1L, content = "b"))

                    val result = chatMessageRepository.findByRoomIdWithCursor(roomId = 1L, cursor = null, size = 10)

                    result.map { it.content } shouldBe listOf("a")
                }
            }
        }
    }
})
