package com.ditto.domain.chat.entity

import com.ditto.domain.chat.ChatVoteFixture
import com.ditto.domain.chat.repository.ChatVoteRepository
import com.ditto.domain.support.IntegrationTest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDateTime
import javax.sql.DataSource

class ChatVoteTest(
    private val chatVoteRepository: ChatVoteRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val closedAt = LocalDateTime.of(2026, 8, 29, 21, 0)

    "ChatVote 생성" - {
        "when: open 으로 만들면" - {
            "then: OPEN 상태로 시작하고 openRoomId 가 방을 가리킨다" {
                val vote = chatVoteRepository.save(ChatVoteFixture.open(roomId = 10L, createdBy = 1L))

                vote.id shouldNotBe 0L
                vote.status shouldBe ChatVoteStatus.OPEN
                vote.isClosed shouldBe false
                vote.openRoomId shouldBe 10L
                vote.closedAt shouldBe null
                vote.closedReason shouldBe null
                vote.closedBy shouldBe null
            }
        }
    }

    "close — 마감" - {
        "when: 마감하면" - {
            "then: 상태·시각·마감자가 기록되고 openRoomId 가 비워진다" {
                val vote = chatVoteRepository.save(ChatVoteFixture.open(roomId = 10L))

                vote.closeByMember(by = 2L, at = closedAt)

                chatVoteRepository.save(vote).let {
                    it.isClosed shouldBe true
                    it.closedReason shouldBe ChatVoteCloseReason.MEMBER
                    it.closedAt shouldBe closedAt
                    it.closedBy shouldBe 2L
                    it.openRoomId shouldBe null
                }
            }
        }

        "given: 이미 마감된 투표일 때" - {
            "when: 다시 마감하면" - {
                "then: 최초 마감 기록이 덮이지 않도록 IllegalStateException 이 발생한다" {
                    val vote = ChatVoteFixture.open(roomId = 10L)
                    vote.closeByMember(by = 1L, at = closedAt)

                    shouldThrow<IllegalStateException> {
                        vote.closeByMember(by = 2L, at = closedAt.plusHours(1))
                    }

                    vote.closedBy shouldBe 1L
                    vote.closedAt shouldBe closedAt
                }
            }
        }
    }

    "방당 열린 투표 1개 (chat_vote_uk_1)" - {
        "given: 방에 열린 투표가 있을 때" - {
            "when: 같은 방에 또 열면" - {
                "then: 유일 제약 충돌로 예외가 발생한다" {
                    chatVoteRepository.save(ChatVoteFixture.open(roomId = 10L))

                    shouldThrow<Exception> {
                        chatVoteRepository.saveAndFlush(ChatVoteFixture.open(roomId = 10L))
                    }
                }
            }
        }

        "given: 방의 투표가 마감돼 openRoomId 가 NULL 일 때" - {
            "when: 같은 방에 새로 열면" - {
                "then: NULL 은 유일 제약에 걸리지 않아 성공한다 — 닫힌 투표는 몇 개든 남는다" {
                    val closed = chatVoteRepository.save(ChatVoteFixture.open(roomId = 10L))
                    closed.closeByMember(by = 1L, at = closedAt)
                    chatVoteRepository.saveAndFlush(closed)

                    val reopened = chatVoteRepository.saveAndFlush(ChatVoteFixture.open(roomId = 10L))

                    reopened.openRoomId shouldBe 10L
                    chatVoteRepository.findAllByRoomIdOrderByIdDesc(10L).size shouldBe 2
                }
            }
        }
    }

    "findByOpenRoomId — 열린 투표 조회" - {
        "given: 열린 투표와 닫힌 투표가 섞여 있을 때" - {
            "then: 열린 것만 돌려준다" {
                val closed = chatVoteRepository.save(ChatVoteFixture.open(roomId = 10L))
                closed.closeByMember(by = 1L, at = closedAt)
                chatVoteRepository.save(closed)
                val open = chatVoteRepository.save(ChatVoteFixture.open(roomId = 10L))

                chatVoteRepository.findByOpenRoomId(10L)?.id shouldBe open.id
            }
        }

        "given: 열린 투표가 없을 때" - {
            "then: null 이다" {
                chatVoteRepository.findByOpenRoomId(99L) shouldBe null
            }
        }
    }
})
