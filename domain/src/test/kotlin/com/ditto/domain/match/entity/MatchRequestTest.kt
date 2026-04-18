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
                MatchRequestFixture.create(fromMemberId = 1L, toMemberId = 2L, quizSetId = 1L),
            )

            request.id shouldNotBe 0L
            request.fromMemberId shouldBe 1L
            request.toMemberId shouldBe 2L
            request.quizSetId shouldBe 1L
            request.status shouldBe MatchRequestStatus.PENDING
            request.respondedAt shouldBe null
        }

        "같은 (fromMemberId, toMemberId, quizSetId) 조합으로 중복 저장하면 예외가 발생한다" {
            matchRequestRepository.save(
                MatchRequestFixture.create(fromMemberId = 1L, toMemberId = 2L, quizSetId = 1L),
            )

            shouldThrow<Exception> {
                matchRequestRepository.saveAndFlush(
                    MatchRequestFixture.create(fromMemberId = 1L, toMemberId = 2L, quizSetId = 1L),
                )
            }
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

        "PENDING이 아닌 상태에서 accept()하면 예외가 발생한다" {
            val request = matchRequestRepository.save(
                MatchRequestFixture.create(status = MatchRequestStatus.REJECTED),
            )

            shouldThrow<WarnException> {
                request.accept()
            }
        }

        "PENDING이 아닌 상태에서 reject()하면 예외가 발생한다" {
            val request = matchRequestRepository.save(
                MatchRequestFixture.create(status = MatchRequestStatus.ACCEPTED),
            )

            shouldThrow<WarnException> {
                request.reject()
            }
        }
    }
})
