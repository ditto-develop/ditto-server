package com.ditto.domain.review.repository

import com.ditto.domain.chat.ChatRoomFixture
import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.review.MemberReviewFixture
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

private val FRIDAY = LocalDateTime.of(2026, 3, 13, 12, 0)
private val AFTER_EXPIRY = LocalDateTime.of(2026, 3, 16, 0, 0)

class MemberReviewRepositoryTest(
    private val memberReviewRepository: MemberReviewRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val memberRepository: MemberRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun saveEndedRoom(room: ChatRoom): ChatRoom =
        chatRoomRepository.save(room).also {
            it.expire(AFTER_EXPIRY)
            chatRoomRepository.save(it)
        }

    "findPendingAvailableBetween — 평가 리마인드 후보" - {
        fun saveMember(nickname: String, status: MemberStatus = MemberStatus.ACTIVE) =
            memberRepository.save(
                MemberFixture.create(nickname = nickname, email = "$nickname@ditto.pics", status = status),
            )

        "창 안에 열린 미완료 평가만 돌려준다" {
            val (a, b, c) = listOf("a", "b", "c").map { saveMember(it) }
            val pending = memberReviewRepository.save(
                MemberReviewFixture.create(authorMemberId = a.id, chatRoomId = 10L, availableAt = AFTER_EXPIRY),
            )
            memberReviewRepository.save(
                MemberReviewFixture.create(authorMemberId = b.id, chatRoomId = 10L, availableAt = AFTER_EXPIRY)
                    .apply { recordAnswer(hasRemainingTarget = false, answeredAt = AFTER_EXPIRY.plusHours(1)) },
            )
            memberReviewRepository.save(
                MemberReviewFixture.create(
                    authorMemberId = c.id,
                    chatRoomId = 11L,
                    availableAt = AFTER_EXPIRY.minusDays(8),
                ),
            )

            val result = memberReviewRepository.findPendingAvailableBetween(AFTER_EXPIRY.minusDays(7), AFTER_EXPIRY)

            result.map { it.id } shouldBe listOf(pending.id)
        }

        "탈퇴한 작성자의 평가는 행이 남아 있어도 돌려주지 않는다" {
            val left = saveMember("left", MemberStatus.LEFT)
            memberReviewRepository.save(
                MemberReviewFixture.create(authorMemberId = left.id, chatRoomId = 10L, availableAt = AFTER_EXPIRY),
            )

            memberReviewRepository.findPendingAvailableBetween(AFTER_EXPIRY.minusDays(7), AFTER_EXPIRY).shouldBeEmpty()
        }

        "창 경계 — from 은 제외, to 는 포함한다" {
            val author = saveMember("a")
            memberReviewRepository.save(
                MemberReviewFixture.create(authorMemberId = author.id, chatRoomId = 10L, availableAt = FRIDAY),
            )

            memberReviewRepository.findPendingAvailableBetween(FRIDAY, AFTER_EXPIRY).shouldBeEmpty()
            memberReviewRepository.findPendingAvailableBetween(FRIDAY.minusDays(1), FRIDAY).size shouldBe 1
        }
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

        "given: 인원 미달로 해체된 그룹 방일 때" - {
            "when: 조회하면" - {
                // 해체 방은 정의상 남은 사람이 1명뿐이라 평가가 영원히 열리지 않는다(2명 미만은 열지 않음).
                // 재매칭 방과 같은 이유로 빼지 않으면 배치 앞자리를 영구 점유한다.
                "then: 대상에서 빠진다" {
                    val room = chatRoomRepository.save(ChatRoomFixture.group(sourceId = 600L, now = FRIDAY))
                    room.endByInsufficientMembers(AFTER_EXPIRY)
                    chatRoomRepository.save(room)

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
