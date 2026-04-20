package com.ditto.domain.match.entity

import com.ditto.common.exception.WarnException
import com.ditto.domain.match.MatchRequestFixture
import com.ditto.domain.match.repository.MatchRequestRepository
import com.ditto.domain.support.IntegrationTest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import javax.sql.DataSource

class MatchRequestTest(
    private val matchRequestRepository: MatchRequestRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "MatchRequest 생성" - {
        "given: requester=1, receiver=2 일 때" - {
            "when: 저장하면" - {
                "then: memberId1=min(1,2), memberId2=max(1,2), requesterId=1 로 정규화되어 저장된다" {
                    val request = matchRequestRepository.save(
                        MatchRequestFixture.create(requesterId = 1L, receiverId = 2L, quizSetId = 1L),
                    )

                    request.id shouldNotBe 0L
                    request.memberId1 shouldBe 1L
                    request.memberId2 shouldBe 2L
                    request.requesterId shouldBe 1L
                    request.receiverId() shouldBe 2L
                    request.status shouldBe MatchRequestStatus.PENDING
                    request.respondedAt shouldBe null
                }
            }
        }

        "given: requester=5, receiver=2 처럼 requester가 더 클 때" - {
            "when: 저장하면" - {
                "then: memberId1=2, memberId2=5 로 자동 정규화된다" {
                    val request = matchRequestRepository.save(
                        MatchRequestFixture.create(requesterId = 5L, receiverId = 2L, quizSetId = 1L),
                    )

                    request.memberId1 shouldBe 2L
                    request.memberId2 shouldBe 5L
                    request.requesterId shouldBe 5L
                    request.receiverId() shouldBe 2L
                }
            }
        }

        "given: A→B 요청이 이미 존재할 때" - {
            "when: B→A 로 역방향 요청을 보내면" - {
                "then: 동일한 UK (memberId1, memberId2, quizSetId) 충돌로 예외가 발생한다" {
                    matchRequestRepository.save(
                        MatchRequestFixture.create(requesterId = 1L, receiverId = 2L, quizSetId = 1L),
                    )

                    shouldThrow<Exception> {
                        matchRequestRepository.saveAndFlush(
                            MatchRequestFixture.create(requesterId = 2L, receiverId = 1L, quizSetId = 1L),
                        )
                    }
                }
            }
        }

        "given: 동일 멤버 페어에 대해 quizSetId가 다를 때" - {
            "when: 각각 요청을 보내면" - {
                "then: 서로 다른 quizSetId이므로 정상 저장된다" {
                    matchRequestRepository.save(
                        MatchRequestFixture.create(requesterId = 1L, receiverId = 2L, quizSetId = 1L),
                    )
                    val request2 = matchRequestRepository.save(
                        MatchRequestFixture.create(requesterId = 1L, receiverId = 2L, quizSetId = 2L),
                    )

                    request2.id shouldNotBe 0L
                }
            }
        }
    }

    "MatchRequest 상태 전이" - {
        "given: PENDING 상태의 요청이 있을 때" - {
            "when: accept() 하면" - {
                "then: ACCEPTED 로 전이되고 respondedAt 이 기록된다" {
                    val request = matchRequestRepository.save(MatchRequestFixture.create())
                    request.accept()

                    request.status shouldBe MatchRequestStatus.ACCEPTED
                    request.respondedAt shouldNotBe null
                }
            }

            "when: reject() 하면" - {
                "then: REJECTED 로 전이되고 respondedAt 이 기록된다" {
                    val request = matchRequestRepository.save(MatchRequestFixture.create())
                    request.reject()

                    request.status shouldBe MatchRequestStatus.REJECTED
                    request.respondedAt shouldNotBe null
                }
            }

            "when: cancel() 하면" - {
                "then: CANCELLED 로 전이된다" {
                    val request = matchRequestRepository.save(MatchRequestFixture.create())
                    request.cancel()

                    request.status shouldBe MatchRequestStatus.CANCELLED
                }
            }

            "when: expire() 하면" - {
                "then: EXPIRED 로 전이된다" {
                    val request = matchRequestRepository.save(MatchRequestFixture.create())
                    request.expire()

                    request.status shouldBe MatchRequestStatus.EXPIRED
                }
            }
        }

        "given: PENDING 이 아닌 상태의 요청이 있을 때" - {
            "when: accept() 또는 reject() 를 시도하면" - {
                "then: WarnException (INVALID_STATUS_TRANSITION) 이 발생한다" {
                    val rejected = matchRequestRepository.save(
                        MatchRequestFixture.create(status = MatchRequestStatus.REJECTED),
                    )
                    val accepted = matchRequestRepository.save(
                        MatchRequestFixture.create(requesterId = 1L, receiverId = 3L, status = MatchRequestStatus.ACCEPTED),
                    )

                    shouldThrow<WarnException> { rejected.accept() }
                    shouldThrow<WarnException> { accepted.reject() }
                }
            }
        }
    }
})
