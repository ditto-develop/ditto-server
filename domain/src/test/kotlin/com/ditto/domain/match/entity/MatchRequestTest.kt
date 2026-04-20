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
        "MatchRequest를 생성하고 저장할 수 있다" {
            val request = matchRequestRepository.save(
                MatchRequestFixture.create(requesterId = 1L, receiverId = 2L, quizSetId = 1L),
            )

            request.id shouldNotBe 0L
            request.memberId1 shouldBe 1L   // min(1, 2)
            request.memberId2 shouldBe 2L   // max(1, 2)
            request.requesterId shouldBe 1L
            request.receiverId() shouldBe 2L
            request.status shouldBe MatchRequestStatus.PENDING
            request.respondedAt shouldBe null
        }

        "memberId 정규화 — requesterId가 더 크면 memberId1/2가 자동으로 min/max로 저장된다" {
            val request = matchRequestRepository.save(
                MatchRequestFixture.create(requesterId = 5L, receiverId = 2L, quizSetId = 1L),
            )

            request.memberId1 shouldBe 2L   // min(5, 2)
            request.memberId2 shouldBe 5L   // max(5, 2)
            request.requesterId shouldBe 5L
            request.receiverId() shouldBe 2L
        }

        "A→B 요청이 있는 상태에서 B→A 요청을 보내면 UK 위반으로 예외가 발생한다" {
            matchRequestRepository.save(
                MatchRequestFixture.create(requesterId = 1L, receiverId = 2L, quizSetId = 1L),
            )

            shouldThrow<Exception> {
                matchRequestRepository.saveAndFlush(
                    MatchRequestFixture.create(requesterId = 2L, receiverId = 1L, quizSetId = 1L),
                )
            }
        }

        "서로 다른 퀴즈셋이면 같은 두 멤버도 각각 요청할 수 있다" {
            matchRequestRepository.save(
                MatchRequestFixture.create(requesterId = 1L, receiverId = 2L, quizSetId = 1L),
            )
            val request2 = matchRequestRepository.save(
                MatchRequestFixture.create(requesterId = 1L, receiverId = 2L, quizSetId = 2L),
            )

            request2.id shouldNotBe 0L
        }
    }

    "MatchRequest 상태 전이" - {
        "PENDING 상태에서 accept()하면 ACCEPTED로 전이된다" {
            val request = matchRequestRepository.save(MatchRequestFixture.create())
            request.accept()

            request.status shouldBe MatchRequestStatus.ACCEPTED
            request.respondedAt shouldNotBe null
        }

        "PENDING 상태에서 reject()하면 REJECTED로 전이된다" {
            val request = matchRequestRepository.save(MatchRequestFixture.create())
            request.reject()

            request.status shouldBe MatchRequestStatus.REJECTED
            request.respondedAt shouldNotBe null
        }

        "PENDING 상태에서 cancel()하면 CANCELLED로 전이된다" {
            val request = matchRequestRepository.save(MatchRequestFixture.create())
            request.cancel()

            request.status shouldBe MatchRequestStatus.CANCELLED
        }

        "PENDING 상태에서 expire()하면 EXPIRED로 전이된다" {
            val request = matchRequestRepository.save(MatchRequestFixture.create())
            request.expire()

            request.status shouldBe MatchRequestStatus.EXPIRED
        }

        "PENDING이 아닌 상태에서 전이를 시도하면 예외가 발생한다" {
            val request = matchRequestRepository.save(
                MatchRequestFixture.create(status = MatchRequestStatus.REJECTED),
            )

            shouldThrow<WarnException> { request.accept() }
            shouldThrow<WarnException> { request.reject() }
        }
    }
})
