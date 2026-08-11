package com.ditto.domain.chat.repository

import com.ditto.domain.chat.ChatRoomFixture
import com.ditto.domain.chat.entity.ChatRoomStatus
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

// 주말 창은 금 00:00 ~ 월 00:00 이다. 금요일 정오에 만든 방은 ACTIVE 이고 월요일 00:00 에 끝난다.
private val FRIDAY_NOON = ChatRoomFixture.DEFAULT_NOW
private val SUNDAY_EVENING = LocalDateTime.of(2026, 3, 15, 20, 0)
private val EXPIRES_AT = LocalDateTime.of(2026, 3, 16, 0, 0)

class ChatRoomEndingSoonQueryTest(
    private val chatRoomRepository: ChatRoomRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "findAllIdsEndingBetween — 종료 임박 알림 후보" - {
        "given: 열려 있고 4시간 뒤에 끝나는 방이 있을 때" - {
            "when: 6시간 창으로 조회하면" - {
                "then: 그 방이 나온다" {
                    val room = chatRoomRepository.save(ChatRoomFixture.personal(now = FRIDAY_NOON))
                    room.status shouldBe ChatRoomStatus.ACTIVE
                    room.expiresAt shouldBe EXPIRES_AT

                    val result = chatRoomRepository
                        .findAllIdsEndingBetween(SUNDAY_EVENING, SUNDAY_EVENING.plusHours(6))

                    result shouldBe listOf(room.id)
                }
            }

            "when: 아직 창 밖일 때 조회하면" - {
                "then: 나오지 않는다 — 6시간 전이 되면 그때 알린다" {
                    chatRoomRepository.save(ChatRoomFixture.personal(now = FRIDAY_NOON))

                    val result = chatRoomRepository
                        .findAllIdsEndingBetween(FRIDAY_NOON, FRIDAY_NOON.plusHours(6))

                    result.size shouldBe 0
                }
            }
        }

        // 아직 열리지 않은 방에 "곧 종료된다"고 알리면 시작도 안 한 대화를 재촉한다.
        "given: 아직 열리지 않은 예약 방일 때" - {
            "when: 조회하면" - {
                "then: 나오지 않는다" {
                    val scheduled = chatRoomRepository.save(
                        ChatRoomFixture.rematch(now = FRIDAY_NOON.minusDays(3)),
                    )
                    scheduled.status shouldBe ChatRoomStatus.SCHEDULED

                    val result = chatRoomRepository
                        .findAllIdsEndingBetween(SUNDAY_EVENING, SUNDAY_EVENING.plusHours(6))

                    result.size shouldBe 0
                }
            }
        }

        "given: 이미 끝난 방일 때" - {
            "when: 조회하면" - {
                "then: 나오지 않는다" {
                    val room = chatRoomRepository.save(ChatRoomFixture.personal(now = FRIDAY_NOON))
                    room.expire(SUNDAY_EVENING)
                    chatRoomRepository.save(room)

                    val result = chatRoomRepository
                        .findAllIdsEndingBetween(SUNDAY_EVENING, SUNDAY_EVENING.plusHours(6))

                    result.size shouldBe 0
                }
            }
        }
    }
})
