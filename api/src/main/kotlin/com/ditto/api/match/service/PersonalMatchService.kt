package com.ditto.api.match.service

import com.ditto.api.chat.service.ChatService
import com.ditto.api.match.MatchWeekPolicy
import com.ditto.api.match.dto.PersonalMatchListResponse
import com.ditto.api.match.dto.PersonalMatchRequest
import com.ditto.api.match.dto.PersonalMatchResponse
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.match.entity.PersonalMatch
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.PersonalMatchRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class PersonalMatchService(
    private val personalMatchRepository: PersonalMatchRepository,
    private val chatService: ChatService,
    private val matchWeekPolicy: MatchWeekPolicy,
) {

    /** 보낸/받은 1:1 매칭 요청 목록 조회 */
    fun getPersonalMatches(memberId: Long, quizSetId: Long): PersonalMatchListResponse {
        val sent = personalMatchRepository.findByRequesterIdAndQuizSetId(memberId, quizSetId)
        val received = findReceivedMatches(memberId, quizSetId)
        return PersonalMatchListResponse(
            sent = sent.map { PersonalMatchResponse.from(it) },
            received = received.map { PersonalMatchResponse.from(it) },
        )
    }

    /**
     * 1:1 매칭 요청 생성.
     *
     * 퀴즈셋을 클라이언트가 보내므로 지난 주 후보에게도 요청이 들어올 수 있다 — 이번 주 퀴즈셋만 받는다.
     */
    @Transactional
    fun requestMatch(requesterId: Long, request: PersonalMatchRequest): PersonalMatchResponse {
        val (receiverId, quizSetId) = request

        if (requesterId == receiverId) {
            throw WarnException(ErrorCode.CANNOT_REQUEST_SELF)
        }
        matchWeekPolicy.validateCurrentWeek(quizSetId)
        validateNeitherMatched(selfId = requesterId, counterpartId = receiverId, quizSetId = quizSetId)

        val memberId1 = minOf(requesterId, receiverId)
        val memberId2 = maxOf(requesterId, receiverId)
        if (personalMatchRepository.existsByMemberId1AndMemberId2AndQuizSetId(memberId1, memberId2, quizSetId)) {
            throw WarnException(ErrorCode.MATCH_REQUEST_ALREADY_EXISTS)
        }

        val match = personalMatchRepository.save(
            PersonalMatch.create(
                requesterId = requesterId,
                receiverId = receiverId,
                quizSetId = quizSetId,
            )
        )
        return PersonalMatchResponse.from(match)
    }

    /** 1:1 매칭 수락. 지난 주 요청을 오늘 수락해 채팅방이 열리지 않도록 주차를 함께 본다. */
    @Transactional
    fun acceptMatch(memberId: Long, matchId: Long): PersonalMatchResponse {
        val match = findMatchOrThrow(matchId)
        validateReceiver(match, memberId)
        matchWeekPolicy.validateCurrentWeek(match.quizSetId)
        validateNeitherMatched(selfId = memberId, counterpartId = match.requesterId, quizSetId = match.quizSetId)
        match.accept()
        chatService.createPersonalRoom(match.id, match.memberId1, match.memberId2)
        return PersonalMatchResponse.from(match)
    }

    /** 1:1 매칭 거절 */
    @Transactional
    fun rejectMatch(memberId: Long, matchId: Long): PersonalMatchResponse {
        val match = findMatchOrThrow(matchId)
        validateReceiver(match, memberId)
        matchWeekPolicy.validateCurrentWeek(match.quizSetId)
        match.reject()
        return PersonalMatchResponse.from(match)
    }

    private fun findMatchOrThrow(matchId: Long): PersonalMatch =
        personalMatchRepository.findById(matchId).orElseThrow { WarnException(ErrorCode.NOT_FOUND) }

    private fun validateReceiver(match: PersonalMatch, memberId: Long) {
        if (match.receiverId() != memberId) {
            throw WarnException(ErrorCode.FORBIDDEN)
        }
    }

    /** 1:1 방은 한 주에 하나다. 둘 중 누가 이번 퀴즈셋에서 이미 성사됐으면 신청도 수락도 받지 않는다. */
    private fun validateNeitherMatched(selfId: Long, counterpartId: Long, quizSetId: Long) {
        if (isMatchedIn(quizSetId, selfId)) {
            throw WarnException(ErrorCode.ALREADY_MATCHED)
        }
        if (isMatchedIn(quizSetId, counterpartId)) {
            throw WarnException(ErrorCode.COUNTERPART_ALREADY_MATCHED)
        }
    }

    private fun isMatchedIn(quizSetId: Long, memberId: Long): Boolean =
        personalMatchRepository.existsMatchByQuizSetIdAndStatusAndMemberId(
            quizSetId = quizSetId,
            status = PersonalMatchStatus.ACCEPTED,
            memberId = memberId,
        )

    private fun findReceivedMatches(memberId: Long, quizSetId: Long): List<PersonalMatch> {
        val receivedAsMember1 = personalMatchRepository
            .findByMemberId1AndQuizSetIdAndRequesterIdNot(memberId, quizSetId, memberId)
        val receivedAsMember2 = personalMatchRepository
            .findByMemberId2AndQuizSetIdAndRequesterIdNot(memberId, quizSetId, memberId)
        return receivedAsMember1 + receivedAsMember2
    }
}
