package com.ditto.domain.rematch.repository

import com.ditto.domain.chat.ChatRoomFixture
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.rematch.RematchFixture
import com.ditto.domain.rematch.entity.Rematch
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

private val MATCHED_AT = LocalDateTime.of(2026, 3, 9, 10, 0)

class RematchRepositoryTest(
    private val rematchRepository: RematchRepository,
    private val chatRoomRepository: ChatRoomRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    /** 양쪽이 서로를 선택해 MATCHED 가 된 쌍. */
    fun saveMatched(
        memberIdA: Long = 1L,
        memberIdB: Long = 2L,
        sourceGroupMatchId: Long = 1L,
        matchedAt: LocalDateTime = MATCHED_AT,
    ): Rematch {
        val rematch = RematchFixture.create(
            sourceGroupMatchId = sourceGroupMatchId,
            memberIdA = memberIdA,
            memberIdB = memberIdB,
        )
        rematch.submitWants(memberIdA, wants = true, now = matchedAt)
        rematch.submitWants(memberIdB, wants = true, now = matchedAt)
        return rematchRepository.save(rematch)
    }

    "findMatchedWithoutChatRoom — 성사됐는데 방이 없는 쌍(방 예약 대상)" - {
        "given: 성사된 쌍이 있고 방이 없을 때" - {
            "when: 조회하면" - {
                "then: 그 쌍을 돌려준다" {
                    val rematch = saveMatched()

                    val found = rematchRepository.findMatchedWithoutChatRoom(100)

                    found.map { it.id } shouldBe listOf(rematch.id)
                }
            }
        }

        "given: 그 쌍의 REMATCH 방이 이미 있을 때" - {
            "when: 조회하면" - {
                // 방이 곧 처리 완료 기록이다 — 별도 표시 없이 이 조회에서 빠지는 것으로 멱등이 성립한다.
                "then: 대상에서 빠진다" {
                    val rematch = saveMatched()
                    chatRoomRepository.save(ChatRoomFixture.rematch(sourceId = rematch.id))

                    rematchRepository.findMatchedWithoutChatRoom(100).size shouldBe 0
                }
            }
        }

        "given: 방이 다른 원본(PERSONAL)으로 같은 숫자의 source_id 를 쓸 때" - {
            "when: 조회하면" - {
                // (source_type, source_id) 쌍으로 판정해야 한다 — source_id 만 보면 엉뚱한 방을 자기 방으로 착각한다.
                "then: 여전히 예약 대상이다" {
                    val rematch = saveMatched()
                    chatRoomRepository.save(ChatRoomFixture.personal(sourceId = rematch.id))

                    rematchRepository.findMatchedWithoutChatRoom(100).map { it.id } shouldBe listOf(rematch.id)
                }
            }
        }

        "given: 아직 성사되지 않았거나 취소된 쌍일 때" - {
            "when: 조회하면" - {
                "then: 대상이 아니다" {
                    // 미응답(WAITING)
                    rematchRepository.save(RematchFixture.create(sourceGroupMatchId = 1L, memberIdA = 1L, memberIdB = 2L))
                    // 한쪽 거절(CANCELLED)
                    val declined = RematchFixture.create(sourceGroupMatchId = 2L, memberIdA = 3L, memberIdB = 4L)
                    declined.submitWants(3L, wants = true, now = MATCHED_AT)
                    declined.submitWants(4L, wants = false, now = MATCHED_AT)
                    rematchRepository.save(declined)

                    rematchRepository.findMatchedWithoutChatRoom(100).size shouldBe 0
                }
            }
        }

        "given: 성사된 쌍이 여럿일 때" - {
            "when: limit 을 주면" - {
                // 오래 밀린 것부터 — 가장 오래 기다린 쌍이 먼저 방을 얻는다.
                "then: 성사가 오래된 순으로 그 수만큼만 돌려준다" {
                    val old = saveMatched(1L, 2L, sourceGroupMatchId = 1L, matchedAt = MATCHED_AT)
                    saveMatched(3L, 4L, sourceGroupMatchId = 2L, matchedAt = MATCHED_AT.plusDays(1))

                    val found = rematchRepository.findMatchedWithoutChatRoom(1)

                    found.map { it.id } shouldBe listOf(old.id)
                }
            }
        }
    }
})
