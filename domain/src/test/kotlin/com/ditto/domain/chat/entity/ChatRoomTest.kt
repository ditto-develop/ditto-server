package com.ditto.domain.chat.entity

import com.ditto.domain.chat.ChatRoomFixture
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.support.IntegrationTest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDateTime
import javax.sql.DataSource

class ChatRoomTest(
    private val chatRoomRepository: ChatRoomRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "ChatRoom 생성" - {
        "when: personal 팩토리로 만들면" - {
            "then: PERSONAL 타입으로 저장된다" {
                val room = chatRoomRepository.save(ChatRoomFixture.personal(sourceId = 10L))

                room.id shouldNotBe 0L
                room.roomType shouldBe ChatRoomType.PERSONAL
                room.sourceId shouldBe 10L
            }
        }

        "when: group 팩토리로 만들면" - {
            "then: GROUP 타입으로 저장된다" {
                val room = chatRoomRepository.save(ChatRoomFixture.group(sourceId = 20L))

                room.roomType shouldBe ChatRoomType.GROUP
                room.sourceId shouldBe 20L
            }
        }

        "given: 같은 (room_type, source_id) 방이 이미 있을 때" - {
            "when: 동일 조합으로 다시 저장하면" - {
                "then: 유니크 제약(chat_room_uk_1) 충돌로 예외가 발생한다" {
                    chatRoomRepository.save(ChatRoomFixture.personal(sourceId = 10L))

                    shouldThrow<Exception> {
                        chatRoomRepository.saveAndFlush(ChatRoomFixture.personal(sourceId = 10L))
                    }
                }
            }
        }
    }

    "채팅 기간과 초기 상태" - {
        "when: 개방된 주말 안에서 만들면" - {
            "then: 곧바로 ACTIVE 다" {
                val friday = LocalDateTime.of(2026, 3, 13, 12, 0)

                val room = chatRoomRepository.save(ChatRoomFixture.personal(now = friday))

                room.status shouldBe ChatRoomStatus.ACTIVE
                room.opensAt shouldBe LocalDateTime.of(2026, 3, 13, 0, 0)
                room.expiresAt shouldBe LocalDateTime.of(2026, 3, 16, 0, 0)
            }
        }

        "when: 개방 전(주중)에 만들면" - {
            "then: SCHEDULED 로 예약되고 그 주 금요일을 가리킨다" {
                val wednesday = LocalDateTime.of(2026, 3, 11, 9, 0)

                val room = chatRoomRepository.save(ChatRoomFixture.personal(now = wednesday))

                room.status shouldBe ChatRoomStatus.SCHEDULED
                room.opensAt shouldBe LocalDateTime.of(2026, 3, 13, 0, 0)
            }
        }
    }

    "종료" - {
        "when: 종료하면" - {
            "then: 상태·시각·사유가 함께 기록된다" {
                val room = chatRoomRepository.save(ChatRoomFixture.personal())
                val endedAt = LocalDateTime.of(2026, 3, 14, 10, 0)

                val ended = room.end(ChatEndReason.USER_ENDED, endedAt)

                ended shouldBe true
                room.isEnded shouldBe true
                room.status shouldBe ChatRoomStatus.ENDED
                room.endedAt shouldBe endedAt
                room.endReason shouldBe ChatEndReason.USER_ENDED
            }
        }

        "given: 이미 종료된 방일 때" - {
            "when: 다시 종료를 시도하면" - {
                "then: false 를 돌려주고 최초 종료 기록이 유지된다" {
                    val room = chatRoomRepository.save(ChatRoomFixture.personal())
                    val firstEndedAt = LocalDateTime.of(2026, 3, 14, 10, 0)
                    room.end(ChatEndReason.USER_ENDED, firstEndedAt)

                    val endedAgain = room.end(ChatEndReason.EXPIRED, LocalDateTime.of(2026, 3, 15, 10, 0))

                    endedAgain shouldBe false
                    room.endedAt shouldBe firstEndedAt
                    room.endReason shouldBe ChatEndReason.USER_ENDED
                }
            }
        }
    }

    "예약 방 개방" - {
        "given: 개방 전 예약된 방일 때" - {
            "when: 개방 시각이 지나 열면" - {
                "then: ACTIVE 로 바뀐다" {
                    val room = chatRoomRepository.save(
                        ChatRoomFixture.personal(now = LocalDateTime.of(2026, 3, 11, 9, 0)),
                    )

                    val opened = room.openIfDue(LocalDateTime.of(2026, 3, 13, 0, 0))

                    opened shouldBe true
                    room.status shouldBe ChatRoomStatus.ACTIVE
                }
            }

            "when: 아직 개방 시각 전이면" - {
                "then: 예약 상태를 유지한다" {
                    val room = chatRoomRepository.save(
                        ChatRoomFixture.personal(now = LocalDateTime.of(2026, 3, 11, 9, 0)),
                    )

                    val opened = room.openIfDue(LocalDateTime.of(2026, 3, 12, 23, 59))

                    opened shouldBe false
                    room.status shouldBe ChatRoomStatus.SCHEDULED
                }
            }
        }

        "given: 이미 종료된 방일 때" - {
            "when: 개방을 시도하면" - {
                "then: 다시 열리지 않는다" {
                    val room = chatRoomRepository.save(ChatRoomFixture.personal())
                    room.end(ChatEndReason.EXPIRED, LocalDateTime.of(2026, 3, 16, 0, 0))

                    val opened = room.openIfDue(LocalDateTime.of(2026, 3, 16, 1, 0))

                    opened shouldBe false
                    room.status shouldBe ChatRoomStatus.ENDED
                }
            }
        }
    }
})
