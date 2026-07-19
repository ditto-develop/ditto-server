package com.ditto.domain.chat.entity

import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import javax.sql.DataSource

class ChatRoomMemberTest(
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "ChatRoomMember 생성" - {
        "when: of 로 만들어 저장하면" - {
            "then: lastReadMessageId 는 null 로 시작한다" {
                val roomMember = chatRoomMemberRepository.save(ChatRoomMember.of(roomId = 1L, memberId = 2L))

                roomMember.id shouldNotBe 0L
                roomMember.roomId shouldBe 1L
                roomMember.memberId shouldBe 2L
                roomMember.lastReadMessageId shouldBe null
            }
        }
    }

    "readUpTo — 읽음 커서 전진(단조 증가)" - {
        "given: 아직 아무것도 안 읽었을 때(null)" - {
            "when: readUpTo(5) 하면" - {
                "then: 5 로 세팅된다" {
                    val roomMember = ChatRoomMember.of(roomId = 1L, memberId = 2L)

                    roomMember.readUpTo(5L)

                    roomMember.lastReadMessageId shouldBe 5L
                }
            }
        }

        "given: 이미 5 까지 읽었을 때" - {
            "when: 더 큰 값 readUpTo(9) 하면" - {
                "then: 9 로 전진한다" {
                    val roomMember = ChatRoomMember.of(roomId = 1L, memberId = 2L)
                    roomMember.readUpTo(5L)

                    roomMember.readUpTo(9L)

                    roomMember.lastReadMessageId shouldBe 9L
                }
            }

            "when: 더 작은 값 readUpTo(3) 하면" - {
                "then: 뒤로 가지 않고 5 를 유지한다" {
                    val roomMember = ChatRoomMember.of(roomId = 1L, memberId = 2L)
                    roomMember.readUpTo(5L)

                    roomMember.readUpTo(3L)

                    roomMember.lastReadMessageId shouldBe 5L
                }
            }
        }
    }
})
