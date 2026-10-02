package com.ditto.api.match

import com.ditto.api.match.dto.PersonalMatchRequest
import com.ditto.api.match.service.PersonalMatchService
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.repository.QuizSetRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDate
import javax.sql.DataSource

class PersonalMatchServiceTest(
    private val personalMatchService: PersonalMatchService,
    private val personalMatchRepository: PersonalMatchRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val quizSetRepository: QuizSetRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    // 요청·수락·거절은 이번 주 퀴즈셋만 받는다(MatchWeekPolicy). 조회 경로는 주차를 보지 않는다.
    fun thisWeekQuizSetId(): Long = quizSetRepository.save(QuizSetFixture.currentWeek()).id

    fun saveAccepted(requesterId: Long, receiverId: Long, quizSetId: Long) = personalMatchRepository.save(
        PersonalMatchFixture.create(
            requesterId = requesterId, receiverId = receiverId, quizSetId = quizSetId,
            status = PersonalMatchStatus.ACCEPTED,
        )
    )

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
        val quizSetId = thisWeekQuizSetId()
        val request = PersonalMatchRequest(receiverId = 2L, quizSetId = quizSetId)

        // when
        val result = personalMatchService.requestMatch(requesterId = 1L, request = request)

        // then
        result.requesterId shouldBe 1L
        result.receiverId shouldBe 2L
        result.quizSetId shouldBe quizSetId
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
        val quizSetId = thisWeekQuizSetId()
        personalMatchRepository.save(
            PersonalMatchFixture.create(requesterId = 1L, receiverId = 2L, quizSetId = quizSetId)
        )
        val request = PersonalMatchRequest(receiverId = 2L, quizSetId = quizSetId)

        // when & then
        shouldThrow<WarnException> {
            personalMatchService.requestMatch(requesterId = 1L, request = request)
        }.errorCode shouldBe ErrorCode.MATCH_REQUEST_ALREADY_EXISTS
    }

    "역방향 PENDING 요청이 있을 때 반대 방향으로 요청해도 MATCH_REQUEST_ALREADY_EXISTS 예외가 발생한다" {
        // given
        val quizSetId = thisWeekQuizSetId()
        personalMatchRepository.save(
            PersonalMatchFixture.create(requesterId = 2L, receiverId = 1L, quizSetId = quizSetId)
        )
        val request = PersonalMatchRequest(receiverId = 2L, quizSetId = quizSetId)

        // when & then
        shouldThrow<WarnException> {
            personalMatchService.requestMatch(requesterId = 1L, request = request)
        }.errorCode shouldBe ErrorCode.MATCH_REQUEST_ALREADY_EXISTS
    }

    "이미 ACCEPTED 매칭이 있는 페어가 다시 요청하면 ALREADY_MATCHED 예외가 발생한다" {
        // given
        val quizSetId = thisWeekQuizSetId()
        saveAccepted(requesterId = 1L, receiverId = 2L, quizSetId = quizSetId)
        val request = PersonalMatchRequest(receiverId = 2L, quizSetId = quizSetId)

        // when & then
        shouldThrow<WarnException> {
            personalMatchService.requestMatch(requesterId = 1L, request = request)
        }.errorCode shouldBe ErrorCode.ALREADY_MATCHED
    }

    "이번 퀴즈셋에서 이미 성사된 회원이 다른 사람에게 신청하면 ALREADY_MATCHED 예외가 발생한다" {
        // given
        val quizSetId = thisWeekQuizSetId()
        saveAccepted(requesterId = 1L, receiverId = 2L, quizSetId = quizSetId)
        val request = PersonalMatchRequest(receiverId = 3L, quizSetId = quizSetId)

        // when & then
        shouldThrow<WarnException> {
            personalMatchService.requestMatch(requesterId = 1L, request = request)
        }.errorCode shouldBe ErrorCode.ALREADY_MATCHED
    }

    "이미 다른 사람과 성사된 회원에게 신청하면 COUNTERPART_ALREADY_MATCHED 예외가 발생하고 신청이 남지 않는다" {
        // given
        val quizSetId = thisWeekQuizSetId()
        saveAccepted(requesterId = 2L, receiverId = 3L, quizSetId = quizSetId)
        val request = PersonalMatchRequest(receiverId = 2L, quizSetId = quizSetId)

        // when & then
        shouldThrow<WarnException> {
            personalMatchService.requestMatch(requesterId = 1L, request = request)
        }.errorCode shouldBe ErrorCode.COUNTERPART_ALREADY_MATCHED

        personalMatchRepository.existsByMemberId1AndMemberId2AndQuizSetId(1L, 2L, quizSetId) shouldBe false
    }

    "다른 퀴즈셋에서 성사된 기록은 이번 주 신청을 막지 않는다" {
        // given
        val quizSetId = thisWeekQuizSetId()
        saveAccepted(requesterId = 1L, receiverId = 2L, quizSetId = quizSetId + 1)
        val request = PersonalMatchRequest(receiverId = 3L, quizSetId = quizSetId)

        // when
        val result = personalMatchService.requestMatch(requesterId = 1L, request = request)

        // then
        result.status shouldBe PersonalMatchStatus.PENDING
    }

    "수신자가 수락하면 상태가 ACCEPTED 로 변경되고 respondedAt 이 기록된다" {
        // given
        val match = personalMatchRepository.save(
            PersonalMatchFixture.create(requesterId = 1L, receiverId = 2L, quizSetId = thisWeekQuizSetId())
        )

        // when
        val result = personalMatchService.acceptMatch(memberId = 2L, matchId = match.id)

        // then
        result.status shouldBe PersonalMatchStatus.ACCEPTED
        result.respondedAt shouldNotBe null
    }

    "수신자가 수락하면 두 회원의 1:1 채팅방이 생성된다" {
        // given
        val match = personalMatchRepository.save(
            PersonalMatchFixture.create(requesterId = 1L, receiverId = 2L, quizSetId = thisWeekQuizSetId())
        )

        // when
        personalMatchService.acceptMatch(memberId = 2L, matchId = match.id)

        // then
        chatRoomRepository.findBySourceTypeAndSourceId(ChatRoomType.PERSONAL, match.id) shouldNotBe null
    }

    "수락하면 두 사람이 이번 퀴즈셋에서 주고받은 다른 대기 신청이 취소된다" {
        // given
        val quizSetId = thisWeekQuizSetId()
        val match = personalMatchRepository.save(
            PersonalMatchFixture.create(requesterId = 1L, receiverId = 2L, quizSetId = quizSetId)
        )
        val receivedByAcceptor = personalMatchRepository.save(
            PersonalMatchFixture.create(requesterId = 3L, receiverId = 2L, quizSetId = quizSetId)
        )
        val sentByAcceptor = personalMatchRepository.save(
            PersonalMatchFixture.create(requesterId = 2L, receiverId = 4L, quizSetId = quizSetId)
        )
        val sentByRequester = personalMatchRepository.save(
            PersonalMatchFixture.create(requesterId = 1L, receiverId = 5L, quizSetId = quizSetId)
        )

        // when
        personalMatchService.acceptMatch(memberId = 2L, matchId = match.id)

        // then
        personalMatchRepository.findById(match.id).get().status shouldBe PersonalMatchStatus.ACCEPTED
        listOf(receivedByAcceptor, sentByAcceptor, sentByRequester).forEach {
            personalMatchRepository.findById(it.id).get().status shouldBe PersonalMatchStatus.CANCELLED
        }
    }

    "수락해도 성사된 두 사람이 끼지 않은 신청이나 다른 퀴즈셋 신청은 그대로다" {
        // given
        val quizSetId = thisWeekQuizSetId()
        val match = personalMatchRepository.save(
            PersonalMatchFixture.create(requesterId = 1L, receiverId = 2L, quizSetId = quizSetId)
        )
        val unrelated = personalMatchRepository.save(
            PersonalMatchFixture.create(requesterId = 3L, receiverId = 4L, quizSetId = quizSetId)
        )
        val otherQuizSet = personalMatchRepository.save(
            PersonalMatchFixture.create(requesterId = 3L, receiverId = 2L, quizSetId = quizSetId + 1)
        )

        // when
        personalMatchService.acceptMatch(memberId = 2L, matchId = match.id)

        // then
        personalMatchRepository.findById(unrelated.id).get().status shouldBe PersonalMatchStatus.PENDING
        personalMatchRepository.findById(otherQuizSet.id).get().status shouldBe PersonalMatchStatus.PENDING
    }

    "이미 다른 사람과 성사된 수신자는 받은 신청을 수락할 수 없고 채팅방도 생기지 않는다" {
        // given
        val quizSetId = thisWeekQuizSetId()
        saveAccepted(requesterId = 2L, receiverId = 3L, quizSetId = quizSetId)
        val match = personalMatchRepository.save(
            PersonalMatchFixture.create(requesterId = 1L, receiverId = 2L, quizSetId = quizSetId)
        )

        // when & then
        shouldThrow<WarnException> {
            personalMatchService.acceptMatch(memberId = 2L, matchId = match.id)
        }.errorCode shouldBe ErrorCode.ALREADY_MATCHED

        personalMatchRepository.findById(match.id).get().status shouldBe PersonalMatchStatus.PENDING
        chatRoomRepository.findBySourceTypeAndSourceId(ChatRoomType.PERSONAL, match.id) shouldBe null
    }

    "신청자가 이미 다른 사람과 성사됐으면 COUNTERPART_ALREADY_MATCHED 예외가 발생한다" {
        // given
        val quizSetId = thisWeekQuizSetId()
        saveAccepted(requesterId = 1L, receiverId = 3L, quizSetId = quizSetId)
        val match = personalMatchRepository.save(
            PersonalMatchFixture.create(requesterId = 1L, receiverId = 2L, quizSetId = quizSetId)
        )

        // when & then
        shouldThrow<WarnException> {
            personalMatchService.acceptMatch(memberId = 2L, matchId = match.id)
        }.errorCode shouldBe ErrorCode.COUNTERPART_ALREADY_MATCHED

        chatRoomRepository.findBySourceTypeAndSourceId(ChatRoomType.PERSONAL, match.id) shouldBe null
    }

    "이미 수락한 신청을 다시 수락하면 ALREADY_MATCHED 예외가 발생한다" {
        // given
        val match = saveAccepted(requesterId = 1L, receiverId = 2L, quizSetId = thisWeekQuizSetId())

        // when & then
        shouldThrow<WarnException> {
            personalMatchService.acceptMatch(memberId = 2L, matchId = match.id)
        }.errorCode shouldBe ErrorCode.ALREADY_MATCHED
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
            PersonalMatchFixture.create(requesterId = 1L, receiverId = 2L, quizSetId = thisWeekQuizSetId())
        )

        // when
        val result = personalMatchService.rejectMatch(memberId = 2L, matchId = match.id)

        // then
        result.status shouldBe PersonalMatchStatus.REJECTED
    }

    "ACCEPTED 상태의 매칭을 거절하려 하면 INVALID_STATUS_TRANSITION 예외가 발생한다" {
        // given
        val match = saveAccepted(requesterId = 1L, receiverId = 2L, quizSetId = thisWeekQuizSetId())

        // when & then
        shouldThrow<WarnException> {
            personalMatchService.rejectMatch(memberId = 2L, matchId = match.id)
        }.errorCode shouldBe ErrorCode.INVALID_STATUS_TRANSITION
    }

    "지난 주 퀴즈셋으로는 매칭을 요청할 수 없다" {
        // given — 지난 주에 마감된 퀴즈셋
        val lastWeek = quizSetRepository.save(
            QuizSetFixture.create(
                startDate = LocalDate.now().minusWeeks(1).atStartOfDay(),
                endDate = LocalDate.now().minusWeeks(1).plusDays(2).atTime(23, 59, 59),
            )
        )
        val request = PersonalMatchRequest(receiverId = 2L, quizSetId = lastWeek.id)

        // when & then
        shouldThrow<WarnException> {
            personalMatchService.requestMatch(requesterId = 1L, request = request)
        }.errorCode shouldBe ErrorCode.NOT_MATCHING_PERIOD
    }

    "지난 주에 받은 요청은 이제 수락할 수 없다 — 지난 사이클 채팅방이 열리면 안 된다" {
        // given
        val lastWeek = quizSetRepository.save(
            QuizSetFixture.create(
                startDate = LocalDate.now().minusWeeks(1).atStartOfDay(),
                endDate = LocalDate.now().minusWeeks(1).plusDays(2).atTime(23, 59, 59),
            )
        )
        val match = personalMatchRepository.save(
            PersonalMatchFixture.create(requesterId = 1L, receiverId = 2L, quizSetId = lastWeek.id)
        )

        // when & then
        shouldThrow<WarnException> {
            personalMatchService.acceptMatch(memberId = 2L, matchId = match.id)
        }.errorCode shouldBe ErrorCode.NOT_MATCHING_PERIOD

        chatRoomRepository.findBySourceTypeAndSourceId(ChatRoomType.PERSONAL, match.id) shouldBe null
    }
})
