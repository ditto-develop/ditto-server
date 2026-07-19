package com.ditto.domain.chat.entity

import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.support.IntegrationTest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import javax.sql.DataSource

class ChatRoomTest(
    private val chatRoomRepository: ChatRoomRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "ChatRoom 생성" - {
        "when: personal 팩토리로 만들면" - {
            "then: PERSONAL 타입으로 저장된다" {
                val room = chatRoomRepository.save(ChatRoom.personal(sourceId = 10L))

                room.id shouldNotBe 0L
                room.roomType shouldBe ChatRoomType.PERSONAL
                room.sourceId shouldBe 10L
            }
        }

        "when: group 팩토리로 만들면" - {
            "then: GROUP 타입으로 저장된다" {
                val room = chatRoomRepository.save(ChatRoom.group(sourceId = 20L))

                room.roomType shouldBe ChatRoomType.GROUP
                room.sourceId shouldBe 20L
            }
        }

        "given: 같은 (room_type, source_id) 방이 이미 있을 때" - {
            "when: 동일 조합으로 다시 저장하면" - {
                "then: 유니크 제약(chat_room_uk_1) 충돌로 예외가 발생한다" {
                    chatRoomRepository.save(ChatRoom.personal(sourceId = 10L))

                    shouldThrow<Exception> {
                        chatRoomRepository.saveAndFlush(ChatRoom.personal(sourceId = 10L))
                    }
                }
            }
        }
    }
})
