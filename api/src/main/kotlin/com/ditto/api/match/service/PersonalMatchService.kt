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
import com.ditto.domain.member.repository.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class PersonalMatchService(
    private val personalMatchRepository: PersonalMatchRepository,
    private val memberRepository: MemberRepository,
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
     * 회원 잠금이 이 트랜잭션의 첫 조회여야 이후 조회가 잠금을 기다리는 동안 커밋된 성사를 본다(ADR 0035).
     */
    @Transactional
    fun requestMatch(requesterId: Long, request: PersonalMatchRequest): PersonalMatchResponse {
        val (receiverId, quizSetId) = request

        if (requesterId == receiverId) {
            throw WarnException(ErrorCode.CANNOT_REQUEST_SELF)
        }
        lockMembersInIdOrder(requesterId, receiverId)
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

    /**
     * 1:1 매칭 수락. 지난 주 요청을 오늘 수락해 채팅방이 열리지 않도록 주차를 함께 본다.
     *
     * 잠글 회원을 알려면 매칭부터 읽어야 해서 잠금 앞에 일반 조회가 하나 있다. REPEATABLE READ에서는
     * 그 조회 시점의 스냅샷이 고정돼 잠금을 기다리는 동안 커밋된 성사가 보이지 않으므로
     * READ COMMITTED로 돈다(ADR 0035).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    fun acceptMatch(memberId: Long, matchId: Long): PersonalMatchResponse {
        val match = findMatchOrThrow(matchId)
        validateReceiver(match, memberId)
        lockMembersInIdOrder(match.memberId1, match.memberId2)
        matchWeekPolicy.validateCurrentWeek(match.quizSetId)
        validateNeitherMatched(selfId = memberId, counterpartId = match.requesterId, quizSetId = match.quizSetId)
        match.accept()
        cancelOtherPendingRequests(match)
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

    /** 같은 사람이 낀 신청·수락을 줄 세운다. id 순서로 잠가 서로 기다리는 교착을 막는다. */
    private fun lockMembersInIdOrder(vararg memberIds: Long) {
        memberIds.sorted().forEach { memberRepository.findWithLockById(it) }
    }

    /** 1:1 방은 한 주에 하나다. */
    private fun validateNeitherMatched(selfId: Long, counterpartId: Long, quizSetId: Long) {
        if (isMatchedIn(quizSetId = quizSetId, memberId = selfId)) {
            throw WarnException(ErrorCode.ALREADY_MATCHED)
        }
        if (isMatchedIn(quizSetId = quizSetId, memberId = counterpartId)) {
            throw WarnException(ErrorCode.COUNTERPART_ALREADY_MATCHED)
        }
    }

    private fun isMatchedIn(quizSetId: Long, memberId: Long): Boolean =
        personalMatchRepository.existsMatchByQuizSetIdAndStatusAndMemberId(
            quizSetId = quizSetId,
            status = PersonalMatchStatus.ACCEPTED,
            memberId = memberId,
        )

    /** 그룹 자동 거절처럼 신청자에게 알리지 않는다. */
    private fun cancelOtherPendingRequests(accepted: PersonalMatch) {
        val pendingRequests = personalMatchRepository.findAllByQuizSetIdAndStatusAndAnyMemberIdIn(
            quizSetId = accepted.quizSetId,
            status = PersonalMatchStatus.PENDING,
            memberIds = listOf(accepted.memberId1, accepted.memberId2),
        )
        pendingRequests
            .filterNot { it.id == accepted.id }
            .forEach { it.cancel() }
    }

    private fun findReceivedMatches(memberId: Long, quizSetId: Long): List<PersonalMatch> {
        val receivedAsMember1 = personalMatchRepository
            .findByMemberId1AndQuizSetIdAndRequesterIdNot(memberId, quizSetId, memberId)
        val receivedAsMember2 = personalMatchRepository
            .findByMemberId2AndQuizSetIdAndRequesterIdNot(memberId, quizSetId, memberId)
        return receivedAsMember1 + receivedAsMember2
    }
}
