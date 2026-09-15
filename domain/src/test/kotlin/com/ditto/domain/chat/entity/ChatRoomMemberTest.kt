package com.ditto.domain.chat.entity

import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.support.IntegrationTest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDateTime
import javax.sql.DataSource

class ChatRoomMemberTest(
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "ChatRoomMember 생성" - {
        "when: of 로 만들어 저장하면" - {
            "then: lastReadMessageId 와 leftAt 은 null 로 시작한다" {
                val roomMember = chatRoomMemberRepository.save(ChatRoomMember.of(roomId = 1L, memberId = 2L))

                roomMember.id shouldNotBe 0L
                roomMember.roomId shouldBe 1L
                roomMember.memberId shouldBe 2L
                roomMember.lastReadMessageId shouldBe null
                roomMember.leftAt shouldBe null
                roomMember.hasLeft shouldBe false
            }
        }
    }

    "leave — 방 이탈" - {
        "when: leave 하면" - {
            "then: leftAt 이 기록되고 hasLeft 가 true 가 된다" {
                val leftAt = LocalDateTime.of(2026, 8, 22, 14, 30)
                val roomMember = chatRoomMemberRepository.save(ChatRoomMember.of(roomId = 1L, memberId = 2L))

                roomMember.leave(leftAt)

                chatRoomMemberRepository.save(roomMember).let {
                    it.leftAt shouldBe leftAt
                    it.hasLeft shouldBe true
                }
            }
        }

        "given: 이미 나간 멤버일 때" - {
            "when: 다시 leave 하면" - {
                "then: 최초 이탈 시각이 덮이지 않도록 IllegalStateException 이 발생한다" {
                    val roomMember = ChatRoomMember.of(roomId = 1L, memberId = 2L)
                    roomMember.leave(LocalDateTime.of(2026, 8, 22, 14, 30))

                    shouldThrow<IllegalStateException> {
                        roomMember.leave(LocalDateTime.of(2026, 8, 22, 15, 0))
                    }
                }
            }
        }
    }

    "readUpTo — 읽음 커서 전진(단조 증가)" - {
        "given: 아직 아무것도 안 읽었을 때(null)" - {
            "when: readUpTo(5) 하면" - {
                "then: 5 로 세팅되고 전진했다고 답한다" {
                    val roomMember = ChatRoomMember.of(roomId = 1L, memberId = 2L)

                    val advanced = roomMember.readUpTo(5L)

                    advanced shouldBe true
                    roomMember.lastReadMessageId shouldBe 5L
                }
            }
        }

        "given: 이미 5 까지 읽었을 때" - {
            "when: 더 큰 값 readUpTo(9) 하면" - {
                "then: 9 로 전진한다" {
                    val roomMember = ChatRoomMember.of(roomId = 1L, memberId = 2L)
                    roomMember.readUpTo(5L)

                    val advanced = roomMember.readUpTo(9L)

                    advanced shouldBe true
                    roomMember.lastReadMessageId shouldBe 9L
                }
            }

            "when: 더 작은 값 readUpTo(3) 하면" - {
                "then: 뒤로 가지 않고 5 를 유지하며 전진하지 않았다고 답한다" {
                    val roomMember = ChatRoomMember.of(roomId = 1L, memberId = 2L)
                    roomMember.readUpTo(5L)

                    val advanced = roomMember.readUpTo(3L)

                    advanced shouldBe false
                    roomMember.lastReadMessageId shouldBe 5L
                }
            }

            "when: 같은 값 readUpTo(5) 를 다시 하면" - {
                "then: 전진하지 않았다고 답한다 — 재시도가 읽음 이벤트를 두 번 내지 않게" {
                    val roomMember = ChatRoomMember.of(roomId = 1L, memberId = 2L)
                    roomMember.readUpTo(5L)

                    roomMember.readUpTo(5L) shouldBe false
                }
            }
        }
    }

    "hasRead — 특정 메시지까지 읽었는지" - {
        "아무것도 안 읽었으면 어떤 메시지도 읽지 않은 것이다" {
            ChatRoomMember.of(roomId = 1L, memberId = 2L).hasRead(1L) shouldBe false
        }

        "커서 이하는 읽은 것, 커서 초과는 안 읽은 것이다" {
            val roomMember = ChatRoomMember.of(roomId = 1L, memberId = 2L)
            roomMember.readUpTo(5L)

            roomMember.hasRead(4L) shouldBe true
            roomMember.hasRead(5L) shouldBe true
            roomMember.hasRead(6L) shouldBe false
        }
    }
})
