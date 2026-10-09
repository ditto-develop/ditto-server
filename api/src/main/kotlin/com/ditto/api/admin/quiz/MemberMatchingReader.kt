package com.ditto.api.admin.quiz

import com.ditto.api.admin.quiz.dto.GroupCandidate
import com.ditto.api.admin.quiz.dto.MatchMissReason
import com.ditto.api.admin.quiz.dto.OneToOneRecords
import com.ditto.api.admin.quiz.dto.ParticipantMatching
import com.ditto.api.admin.quiz.dto.QuizSetParticipation
import com.ditto.domain.match.entity.GroupMatch
import com.ditto.domain.match.entity.GroupMatchMember
import com.ditto.domain.match.entity.PersonalMatch
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QuizProgress
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/** 회원 한 명이 참여한 퀴즈셋마다 참여 현황과 같은 매칭 칸을 채운다. 1:1 풀 전체는 다시 계산하지 않는다. */
@Component
class MemberMatchingReader(
    private val matchCandidateRepository: MatchCandidateRepository,
    private val personalMatchRepository: PersonalMatchRepository,
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    private val memberRepository: MemberRepository,
) {
    fun readMatchingByQuizSetId(
        member: Member,
        participations: List<QuizSetParticipation>,
    ): Map<Long, ParticipantMatching> {
        val (oneToOne, group) = participations.partition { it.quizSet.matchingType == MatchingType.ONE_TO_ONE }
        return readOneToOne(member, oneToOne) + readGroup(member, group)
    }

    private fun readOneToOne(
        member: Member,
        participations: List<QuizSetParticipation>,
    ): Map<Long, ParticipantMatching> {
        if (participations.isEmpty()) return emptyMap()

        val quizSetIds = participations.map { it.quizSet.id }
        val candidatesByQuizSetId = matchCandidateRepository
            .findByQuizSetIdInAndOwnerMemberIdIn(quizSetIds, listOf(member.id))
            .groupBy { it.quizSetId }
        val requestsByQuizSetId = personalMatchRepository
            .findByMemberId1InOrMemberId2In(listOf(member.id), listOf(member.id))
            .groupBy { it.quizSetId }
        // 후보가 없는 셋만 이유를 따지므로 그 셋들의 생성 시각만 읽는다.
        val generatedAtByQuizSetId = findOneToOneGeneratedTimes(quizSetIds - candidatesByQuizSetId.keys)
        val nicknames = loadNicknames(
            candidatesByQuizSetId.values.flatten().map { it.otherMemberId } +
                requestsByQuizSetId.values.flatten().map { it.counterpartOf(member.id) },
        )

        return participations.associate { participation ->
            val quizSetId = participation.quizSet.id
            val candidates = candidatesByQuizSetId[quizSetId].orEmpty()
            val requests = requestsByQuizSetId[quizSetId].orEmpty()
            val missReason = if (candidates.isEmpty()) {
                oneToOneMissOf(member, participation.progress, requests, generatedAtByQuizSetId[quizSetId])
            } else {
                null
            }
            val records = OneToOneRecords(member.id, candidates, requests)
            quizSetId to ParticipantMatching.ofOneToOne(records, nicknames, missReason)
        }
    }

    private fun oneToOneMissOf(
        member: Member,
        progress: QuizProgress,
        requests: List<PersonalMatch>,
        generatedAt: LocalDateTime?,
    ): MatchMissReason {
        val prePoolMissReason = PrePoolMiss.of(progress, member, generatedAt)
        if (prePoolMissReason != null) return prePoolMissReason

        return when {
            !member.isActive() -> MatchMissReason.EXCLUDED_INACTIVE
            requests.any { it.status == PersonalMatchStatus.ACCEPTED } -> MatchMissReason.EXCLUDED_ALREADY_MATCHED
            // 후보가 0건이어도 매칭은 돌았을 수 있어 생성 시각이 없다고 매칭 전으로 보지 않는다.
            else -> MatchMissReason.POOL_REASON_NOT_COMPUTED
        }
    }

    private fun readGroup(member: Member, participations: List<QuizSetParticipation>): Map<Long, ParticipantMatching> {
        if (participations.isEmpty()) return emptyMap()

        val quizSetIds = participations.map { it.quizSet.id }.toSet()
        val myGroupMatches = findInvitedGroupMatches(member).filter { it.quizSetId in quizSetIds }
        val invitationsByGroupMatchId = findInvitations(myGroupMatches).groupBy { it.roomId }
        val generatedAtByQuizSetId = findGroupGeneratedTimes(quizSetIds - myGroupMatches.map { it.quizSetId }.toSet())
        val nicknames = loadNicknames(invitationsByGroupMatchId.values.flatten().map { it.memberId })

        return participations.associate { participation ->
            val quizSetId = participation.quizSet.id
            val groupCandidates = myGroupMatches
                .filter { it.quizSetId == quizSetId }
                .map { GroupCandidate.of(it, invitationsByGroupMatchId[it.id].orEmpty(), member.id, nicknames) }
            val missReason = if (groupCandidates.isEmpty()) {
                groupMissOf(member, participation.progress, generatedAtByQuizSetId[quizSetId])
            } else {
                null
            }
            quizSetId to ParticipantMatching(groupCandidates = groupCandidates, missReason = missReason)
        }
    }

    // 참여 현황(AdminParticipantMatchingReader.groupMatchingOf)과 같은 규칙이다. 한쪽을 고치면 같이 고친다.
    private fun groupMissOf(member: Member, progress: QuizProgress, generatedAt: LocalDateTime?): MatchMissReason {
        val prePoolMissReason = PrePoolMiss.of(progress, member, generatedAt)
        if (prePoolMissReason != null) return prePoolMissReason
        if (generatedAt == null) return MatchMissReason.NOT_GENERATED
        return MatchMissReason.NOT_ASSIGNED_TO_GROUP
    }

    private fun findInvitedGroupMatches(member: Member): List<GroupMatch> {
        val groupMatchIds = groupMatchMemberRepository.findByMemberIdIn(listOf(member.id)).map { it.roomId }
        if (groupMatchIds.isEmpty()) return emptyList()
        return groupMatchRepository.findAllById(groupMatchIds)
    }

    private fun findInvitations(groupMatches: List<GroupMatch>): List<GroupMatchMember> {
        if (groupMatches.isEmpty()) return emptyList()
        return groupMatchMemberRepository.findByRoomIdIn(groupMatches.map { it.id })
    }

    private fun findOneToOneGeneratedTimes(quizSetIds: Collection<Long>): Map<Long, LocalDateTime> =
        quizSetIds
            .mapNotNull { quizSetId -> matchCandidateRepository.findFirstByQuizSetIdOrderByIdAsc(quizSetId) }
            .associate { it.quizSetId to it.createdAt }

    private fun findGroupGeneratedTimes(quizSetIds: Collection<Long>): Map<Long, LocalDateTime> {
        if (quizSetIds.isEmpty()) return emptyMap()
        return groupMatchRepository.findByQuizSetIdIn(quizSetIds)
            .groupBy { it.quizSetId }
            .mapValues { (_, groupMatches) -> groupMatches.minOf { it.createdAt } }
    }

    private fun loadNicknames(memberIds: List<Long>): Map<Long, String> {
        if (memberIds.isEmpty()) return emptyMap()
        return memberRepository.findAllById(memberIds.toSet()).associate { it.id to it.nickname }
    }
}
