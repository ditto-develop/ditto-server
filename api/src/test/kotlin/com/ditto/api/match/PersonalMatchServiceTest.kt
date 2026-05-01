package com.ditto.api.match

import com.ditto.api.match.dto.PersonalMatchRequest
import com.ditto.api.match.service.PersonalMatchService
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.PersonalMatchRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import javax.sql.DataSource

class PersonalMatchServiceTest(
    private val personalMatchService: PersonalMatchService,
    private val personalMatchRepository: PersonalMatchRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "보낸/받은 요청이 모두 있을 때 퀴즈셋 기준으로 분리하여 반환한다" {
        // given
        val requesterId = 1L
        val quizSetId = 10L
        personalMatchRepository.save(
            PersonalMatchFixture.create(requesterId = requesterId, receiverId = 2L, quizSetId = quizSetId)
        )
        personalMatchRepository.save(
            PersonalMatchFixture.create(requesterId = 3L, receiverId = requesterId, quizSetId = quizSetId)
        )

        // when
        val result = personalMatchService.getPersonalMatches(requesterId, quizSetId)

        // then
        result.sent.size shouldBe 1
        result.sent[0].requesterId shouldBe requesterId
        result.received.size shouldBe 1
        result.received[0].receiverId shouldBe requesterId
    }

    "다른 퀴즈셋의 요청은 조회 결과에 포함되지 않는다" {
        // given
        val requesterId = 1L
        personalMatchRepository.save(
            PersonalMatchFixture.create(requesterId = requesterId, receiverId = 2L, quizSetId = 10L)
        )
        personalMatchRepository.save(
            PersonalMatchFixture.create(requesterId = requesterId, receiverId = 3L, quizSetId = 20L)
        )

        // when
        val result = personalMatchService.getPersonalMatches(requesterId, 10L)

        // then
        result.sent.size shouldBe 1
        result.sent[0].quizSetId shouldBe 10L
    }

    "정상적인 매칭 요청 시 PENDING 상태의 매칭이 생성된다" {
        // given
        val request = PersonalMatchRequest(receiverId = 2L, quizSetId = 10L)

        // when
        val result = personalMatchService.requestMatch(requesterId = 1L, request = request)

        // then
        result.requesterId shouldBe 1L
        result.receiverId shouldBe 2L
        result.quizSetId shouldBe 10L
        result.status shouldBe PersonalMatchStatus.PENDING
    }

    "자기 자신에게 매칭 요청하면 CANNOT_REQUEST_SELF 예외가 발생한다" {
        // given
        val request = PersonalMatchRequest(receiverId = 1L, quizSetId = 10L)

        // when & then
        shouldThrow<WarnException> {
            personalMatchService.requestMatch(requesterId = 1L, request = request)
        }.errorCode shouldBe ErrorCode.CANNOT_REQUEST_SELF
    }

    "이미 PENDING 요청이 있는 동일 페어가 다시 요청하면 MATCH_REQUEST_ALREADY_EXISTS 예외가 발생한다" {
        // given
        personalMatchRepository.save(
            PersonalMatchFixture.create(requesterId = 1L, receiverId = 2L, quizSetId = 10L)
        )
        val request = PersonalMatchRequest(receiverId = 2L, quizSetId = 10L)

        // when & then
        shouldThrow<WarnException> {
            personalMatchService.requestMatch(requesterId = 1L, request = request)
        }.errorCode shouldBe ErrorCode.MATCH_REQUEST_ALREADY_EXISTS
    }

    "역방향 PENDING 요청이 있을 때 반대 방향으로 요청해도 MATCH_REQUEST_ALREADY_EXISTS 예외가 발생한다" {
        // given
        personalMatchRepository.save(
            PersonalMatchFixture.create(requesterId = 2L, receiverId = 1L, quizSetId = 10L)
        )
        val request = PersonalMatchRequest(receiverId = 2L, quizSetId = 10L)

        // when & then
        shouldThrow<WarnException> {
            personalMatchService.requestMatch(requesterId = 1L, request = request)
        }.errorCode shouldBe ErrorCode.MATCH_REQUEST_ALREADY_EXISTS
    }

    "이미 ACCEPTED 매칭이 있는 페어가 다시 요청하면 ALREADY_MATCHED 예외가 발생한다" {
        // given
        personalMatchRepository.save(
            PersonalMatchFixture.create(
                requesterId = 1L, receiverId = 2L, quizSetId = 10L,
                status = PersonalMatchStatus.ACCEPTED,
            )
        )
        val request = PersonalMatchRequest(receiverId = 2L, quizSetId = 10L)

        // when & then
        shouldThrow<WarnException> {
            personalMatchService.requestMatch(requesterId = 1L, request = request)
        }.errorCode shouldBe ErrorCode.ALREADY_MATCHED
    }

    "수신자가 수락하면 상태가 ACCEPTED 로 변경되고 respondedAt 이 기록된다" {
        // given
        val match = personalMatchRepository.save(
            PersonalMatchFixture.create(requesterId = 1L, receiverId = 2L, quizSetId = 10L)
        )

        // when
        val result = personalMatchService.acceptMatch(memberId = 2L, matchId = match.id)

        // then
        result.status shouldBe PersonalMatchStatus.ACCEPTED
        result.respondedAt shouldNotBe null
    }

    "수신자가 아닌 사용자가 수락을 시도하면 FORBIDDEN 예외가 발생한다" {
        // given
        val match = personalMatchRepository.save(
            PersonalMatchFixture.create(requesterId = 1L, receiverId = 2L, quizSetId = 10L)
        )

        // when & then
        shouldThrow<WarnException> {
            personalMatchService.acceptMatch(memberId = 99L, matchId = match.id)
        }.errorCode shouldBe ErrorCode.FORBIDDEN
    }

    "존재하지 않는 매칭 ID로 수락하면 NOT_FOUND 예외가 발생한다" {
        // when & then
        shouldThrow<WarnException> {
            personalMatchService.acceptMatch(memberId = 1L, matchId = 9999L)
        }.errorCode shouldBe ErrorCode.NOT_FOUND
    }

    "수신자가 거절하면 상태가 REJECTED 로 변경된다" {
        // given
        val match = personalMatchRepository.save(
            PersonalMatchFixture.create(requesterId = 1L, receiverId = 2L, quizSetId = 10L)
        )

        // when
        val result = personalMatchService.rejectMatch(memberId = 2L, matchId = match.id)

        // then
        result.status shouldBe PersonalMatchStatus.REJECTED
    }

    "ACCEPTED 상태의 매칭을 거절하려 하면 INVALID_STATUS_TRANSITION 예외가 발생한다" {
        // given
        val match = personalMatchRepository.save(
            PersonalMatchFixture.create(
                requesterId = 1L, receiverId = 2L, quizSetId = 10L,
                status = PersonalMatchStatus.ACCEPTED,
            )
        )

        // when & then
        shouldThrow<WarnException> {
            personalMatchService.rejectMatch(memberId = 2L, matchId = match.id)
        }.errorCode shouldBe ErrorCode.INVALID_STATUS_TRANSITION
    }
})
