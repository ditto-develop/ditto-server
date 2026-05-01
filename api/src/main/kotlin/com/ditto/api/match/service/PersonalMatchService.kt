package com.ditto.api.match.service

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

    /** 1:1 매칭 요청 생성 */
    @Transactional
    fun requestMatch(requesterId: Long, request: PersonalMatchRequest): PersonalMatchResponse {
        val (receiverId, quizSetId) = request

        if (requesterId == receiverId) {
            throw WarnException(ErrorCode.CANNOT_REQUEST_SELF)
        }

        val memberId1 = minOf(requesterId, receiverId)
        val memberId2 = maxOf(requesterId, receiverId)

        if (personalMatchRepository.existsByMemberId1AndMemberId2AndQuizSetId(memberId1, memberId2, quizSetId)) {
            val existing = personalMatchRepository.findByMemberId1AndMemberId2AndQuizSetIdAndStatus(
                memberId1, memberId2, quizSetId, PersonalMatchStatus.ACCEPTED
            )
            if (existing != null) {
                throw WarnException(ErrorCode.ALREADY_MATCHED)
            }
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

    /** 1:1 매칭 수락 */
    @Transactional
    fun acceptMatch(memberId: Long, matchId: Long): PersonalMatchResponse {
        val match = findMatchOrThrow(matchId)
        validateReceiver(match, memberId)
        match.accept()
        return PersonalMatchResponse.from(match)
    }

    /** 1:1 매칭 거절 */
    @Transactional
    fun rejectMatch(memberId: Long, matchId: Long): PersonalMatchResponse {
        val match = findMatchOrThrow(matchId)
        validateReceiver(match, memberId)
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

    private fun findReceivedMatches(memberId: Long, quizSetId: Long): List<PersonalMatch> {
        val receivedAsMember1 = personalMatchRepository
            .findByMemberId1AndQuizSetIdAndRequesterIdNot(memberId, quizSetId, memberId)
        val receivedAsMember2 = personalMatchRepository
            .findByMemberId2AndQuizSetIdAndRequesterIdNot(memberId, quizSetId, memberId)
        return receivedAsMember1 + receivedAsMember2
    }
}
