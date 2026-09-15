package com.ditto.api.match.service

import com.ditto.api.match.MatchWeekPolicy
import com.ditto.api.match.dto.Candidate
import com.ditto.api.match.dto.CandidateGroup
import com.ditto.api.match.dto.GroupCandidateResponse
import com.ditto.api.match.matching.MatchScore
import com.ditto.api.match.matching.MatchScoreCalculator
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.intronote.entity.IntroQuestion
import com.ditto.domain.intronote.repository.IntroNoteRepository
import com.ditto.domain.match.entity.GroupMatch
import com.ditto.domain.match.entity.GroupMatchMember
import com.ditto.domain.match.entity.InvitationStatus
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.repository.QuizAnswerRepository
import com.ditto.domain.quiz.repository.QuizRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.math.roundToInt

/**
 * 회원에게 노출할 그룹 매칭 후보 조회.
 *
 * 후보 그룹은 마감된 퀴즈셋에만 생성되므로, 1:1과 같은 방식으로 회원이 **이번 운영 주에**
 * 완주(COMPLETED)한 그룹 퀴즈셋을 기준으로 찾는다. 이번 주에 그룹 퀴즈를 풀지 않았으면 NOT_FOUND.
 */
@Service
@Transactional(readOnly = true)
class GroupCandidateService(
    private val matchWeekPolicy: MatchWeekPolicy,
    private val quizRepository: QuizRepository,
    private val quizAnswerRepository: QuizAnswerRepository,
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    private val memberRepository: MemberRepository,
    private val introNoteRepository: IntroNoteRepository,
) {

    fun getGroupCandidates(memberId: Long): GroupCandidateResponse {
        val quizSet = matchWeekPolicy.findCompletedQuizSet(memberId, MatchingType.GROUP)
            ?: throw WarnException(ErrorCode.NOT_FOUND)

        val myMemberships = groupMatchMemberRepository
            .findByMemberIdAndQuizSetId(memberId, quizSet.id)
            .filterNot { it.status == InvitationStatus.DECLINED }
        if (myMemberships.isEmpty()) return GroupCandidateResponse.of(quizSet.id, quizSet.operationWeek, emptyList())

        val roomIds = myMemberships.map { it.roomId }
        val roomsById = groupMatchRepository.findAllById(roomIds).associateBy { it.id }
        val membersByRoomId = groupMatchMemberRepository.findByRoomIdIn(roomIds).groupBy { it.roomId }

        val peerIds = membersByRoomId.values.flatten().map { it.memberId }.toSet() - memberId
        val scoreByPeerId = scoreAgainstMe(memberId, peerIds, quizSet.id)
        val peerCards = toCandidateCards(peerIds, scoreByPeerId)

        return GroupCandidateResponse.of(
            quizSetId = quizSet.id,
            operationWeek = quizSet.operationWeek,
            groups = myMemberships
                .mapNotNull { membership ->
                    val room = roomsById[membership.roomId] ?: return@mapNotNull null
                    toCandidateGroup(room, membership, membersByRoomId[room.id].orEmpty(), memberId, peerCards)
                }
                .sortedByDescending { roomsById.getValue(it.groupMatchId).score },
        )
    }

    /** 나와 각 구성원의 답변 일치도. 화면이 그룹 평균과 개인별 수치를 모두 쓰므로 개인 단위로 계산한다. */
    private fun scoreAgainstMe(memberId: Long, peerIds: Set<Long>, quizSetId: Long): Map<Long, MatchScore> {
        val quizIds = quizRepository.findByQuizSetIdInOrderByDisplayOrderAsc(listOf(quizSetId)).map { it.id }
        val answersByMemberId = quizAnswerRepository
            .findByMemberIdInAndQuizIdIn((peerIds + memberId).toList(), quizIds)
            .groupBy { it.memberId }
            .mapValues { (_, answers) -> answers.associate { it.quizId to it.choiceId } }

        val myAnswers = answersByMemberId[memberId].orEmpty()
        return peerIds.associateWith { peerId ->
            MatchScoreCalculator.calculate(myAnswers, answersByMemberId[peerId].orEmpty())
        }
    }

    private fun toCandidateCards(peerIds: Set<Long>, scoreByPeerId: Map<Long, MatchScore>): Map<Long, Candidate> {
        if (peerIds.isEmpty()) return emptyMap()

        val membersById = memberRepository.findAllById(peerIds).associateBy { it.id }
        val introductionsByMemberId = loadOneWordIntroductions(peerIds.toList())

        return peerIds.mapNotNull { peerId ->
            val member: Member = membersById[peerId] ?: return@mapNotNull null
            peerId to Candidate.of(
                member = member,
                introduction = introductionsByMemberId[peerId],
                matchScore = scoreByPeerId.getValue(peerId),
            )
        }.toMap()
    }

    /** 구성원의 소개노트 ONE_WORD 답변을 한 번에 조회해 회원ID로 매핑한다. 공백 답변은 제외. */
    private fun loadOneWordIntroductions(memberIds: List<Long>): Map<Long, String> =
        introNoteRepository
            .findByMemberIdInAndQuestion(memberIds, IntroQuestion.ONE_WORD)
            .mapNotNull { note -> note.answer.ifBlank { null }?.let { note.memberId to it } }
            .toMap()

    private fun toCandidateGroup(
        room: GroupMatch,
        myMembership: GroupMatchMember,
        roomMembers: List<GroupMatchMember>,
        memberId: Long,
        peerCards: Map<Long, Candidate>,
    ): CandidateGroup? {
        val peers = roomMembers
            .map { it.memberId }
            .filterNot { it == memberId }
            .mapNotNull { peerCards[it] }
            .sortedByDescending { it.scoreBreakdown.matchedQuestions }
        if (peers.isEmpty()) return null

        return CandidateGroup(
            groupMatchId = room.id,
            myStatus = myMembership.status,
            isFormed = room.isActive,
            averageMatchedQuestions = peers.map { it.scoreBreakdown.matchedQuestions }.average().roundToInt(),
            totalQuestions = peers.first().scoreBreakdown.totalQuestions,
            members = peers,
        )
    }
}
