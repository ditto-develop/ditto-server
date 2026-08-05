package com.ditto.domain.review.repository

import com.ditto.domain.chat.ChatRoomFixture
import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.review.MemberReviewFixture
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

private val FRIDAY = LocalDateTime.of(2026, 3, 13, 12, 0)
private val AFTER_EXPIRY = LocalDateTime.of(2026, 3, 16, 0, 0)

class MemberReviewRepositoryTest(
    private val memberReviewRepository: MemberReviewRepository,
    private val chatRoomRepository: ChatRoomRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun saveEndedRoom(room: ChatRoom): ChatRoom =
        chatRoomRepository.save(room).also {
            it.expire(AFTER_EXPIRY)
            chatRoomRepository.save(it)
        }

    "findEndedChatRoomIdsWithoutReview — 끝났는데 평가가 열리지 않은 방(누락 복구 대상)" - {
        "given: 끝난 1:1 방에 평가가 없을 때" - {
            "when: 조회하면" - {
                "then: 그 방을 돌려준다" {
                    val room = saveEndedRoom(ChatRoomFixture.personal(sourceId = 100L, now = FRIDAY))

                    memberReviewRepository.findEndedChatRoomIdsWithoutReview(100) shouldBe listOf(room.id)
                }
            }
        }

        "given: 그 방에 평가가 이미 있을 때" - {
            "when: 조회하면" - {
                "then: 대상에서 빠진다" {
                    val room = saveEndedRoom(ChatRoomFixture.personal(sourceId = 100L, now = FRIDAY))
                    memberReviewRepository.save(MemberReviewFixture.create(chatRoomId = room.id, authorMemberId = 1L))

                    memberReviewRepository.findEndedChatRoomIdsWithoutReview(100).size shouldBe 0
                }
            }
        }

        "given: 아직 끝나지 않은 방일 때" - {
            "when: 조회하면" - {
                "then: 대상이 아니다" {
                    chatRoomRepository.save(ChatRoomFixture.personal(sourceId = 100L, now = FRIDAY))

                    memberReviewRepository.findEndedChatRoomIdsWithoutReview(100).size shouldBe 0
                }
            }
        }

        "given: 끝난 재매칭 방일 때" - {
            "when: 조회하면" - {
                // 재매칭 채팅은 평가를 열지 않으므로 영원히 "평가 없음"이다. 빼지 않으면 종료 시각
                // 오름차순의 앞자리를 영구 점유해 그 뒤에 끝난 방이 복구되지 못한다.
                "then: 대상에서 빠진다" {
                    saveEndedRoom(ChatRoomFixture.rematch(sourceId = 500L, now = FRIDAY))

                    memberReviewRepository.findEndedChatRoomIdsWithoutReview(100).size shouldBe 0
                }
            }
        }

        "given: 정상 방과 재매칭 방이 섞여 있을 때" - {
            "when: 조회하면" - {
                "then: 평가를 여는 유형만 남는다" {
                    val personal = saveEndedRoom(ChatRoomFixture.personal(sourceId = 100L, now = FRIDAY))
                    val group = saveEndedRoom(ChatRoomFixture.group(sourceId = 200L, now = FRIDAY))
                    saveEndedRoom(ChatRoomFixture.rematch(sourceId = 500L, now = FRIDAY))

                    memberReviewRepository.findEndedChatRoomIdsWithoutReview(100)
                        .toSet() shouldBe setOf(personal.id, group.id)
                }
            }
        }
    }
})
