package com.ditto.api.admin.quiz

import com.ditto.api.admin.quiz.dto.GroupCandidate
import com.ditto.api.admin.quiz.dto.MatchMiss
import com.ditto.api.admin.quiz.dto.MatchMissReason
import com.ditto.api.admin.quiz.dto.OneToOneRecords
import com.ditto.api.admin.quiz.dto.ParticipantMatching
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
import com.ditto.domain.quiz.entity.QuizSet
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 회원 한 명이 참여한 퀴즈셋마다 매칭 칸을 채운다. 참여 현황([AdminParticipantMatchingReader])과 같은 칸을 그리지만
 * 1:1 후보가 없는 이유는 풀 전체를 다시 계산하지 않고, 풀 단계에서 빠졌으면 [MatchMissReason.POOL_REASON_NOT_COMPUTED]로 둔다.
 */
@Component
class MemberMatchingReader(
    private val matchCandidateRepository: MatchCandidateRepository,
    private val personalMatchRepository: PersonalMatchRepository,
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    private val memberRepository: MemberRepository,
) {
    fun readByQuizSetId(member: Member, participations: List<QuizSetParticipation>): Map<Long, ParticipantMatching> {
        val (oneToOne, group) = participations.partition { it.quizSet.matchingType == MatchingType.ONE_TO_ONE }
        return readOneToOne(member, oneToOne) + readGroup(member, group)
    }

    private fun readOneToOne(
        member: Member,
        participation: List<QuizSetParticipation>,
    ): Map<Long, ParticipantMatching> {
        if (participation.isEmpty()) return emptyMap()

        val quizSetIds = participation.map { it.quizSet.id }
        val candidatesByQuizSetId = matchCandidateRepository
            .findByQuizSetIdInAndOwnerMemberIdIn(quizSetIds, listOf(member.id))
            .groupBy { it.quizSetId }
        val requestsByQuizSetId = personalMatchRepository
            .findByMemberId1InOrMemberId2In(listOf(member.id), listOf(member.id))
            .groupBy { it.quizSetId }
        val generatedAtByQuizSetId = findCandidateGeneratedTimes(quizSetIds - candidatesByQuizSetId.keys)
        val nicknames = loadNicknames(
            candidatesByQuizSetId.values.flatten().map { it.otherMemberId } +
                requestsByQuizSetId.values.flatten().map { it.counterpartOf(member.id) },
        )

        return participation.associate { participation ->
            val quizSetId = participation.quizSet.id
            val candidates = candidatesByQuizSetId[quizSetId].orEmpty()
            val requests = requestsByQuizSetId[quizSetId].orEmpty()
            val miss = if (candidates.isEmpty()) {
                oneToOneMissOf(member, participation.progress, requests, generatedAtByQuizSetId[quizSetId])
            } else {
                null
            }
            val records = OneToOneRecords(member.id, candidates, requests)
            quizSetId to ParticipantMatching.ofOneToOne(records, nicknames, miss)
        }
    }

    // 1:1 제외 정책(OneToOneExclusionPolicy)이 보는 두 조건까지는 이 회원 기록만으로 알 수 있다.
    private fun oneToOneMissOf(
        member: Member,
        progress: QuizProgress,
        requests: List<PersonalMatch>,
        generatedAt: LocalDateTime?,
    ): MatchMiss {
        val prePoolMiss = PrePoolMiss.of(progress, member, generatedAt)
        if (prePoolMiss != null) return prePoolMiss

        val reason = when {
            !member.isActive() -> MatchMissReason.EXCLUDED_INACTIVE
            requests.any { it.status == PersonalMatchStatus.ACCEPTED } -> MatchMissReason.EXCLUDED_ALREADY_MATCHED
            generatedAt == null -> MatchMissReason.NOT_GENERATED
            else -> MatchMissReason.POOL_REASON_NOT_COMPUTED
        }
        return MatchMiss(reason)
    }

    private fun readGroup(member: Member, participations: List<QuizSetParticipation>): Map<Long, ParticipantMatching> {
        if (participations.isEmpty()) return emptyMap()

        val quizSetIds = participations.map { it.quizSet.id }.toSet()
        val myGroupMatches = findGroupMatchesInvited(member).filter { it.quizSetId in quizSetIds }
        val invitationsByGroupMatchId = findInvitations(myGroupMatches).groupBy { it.roomId }
        val generatedAtByQuizSetId = findGroupGeneratedTimes(quizSetIds - myGroupMatches.map { it.quizSetId }.toSet())
        val nicknames = loadNicknames(invitationsByGroupMatchId.values.flatten().map { it.memberId })

        return participations.associate { participation ->
            val quizSetId = participation.quizSet.id
            val groupCandidates = myGroupMatches
                .filter { it.quizSetId == quizSetId }
                .map { GroupCandidate.of(it, invitationsByGroupMatchId[it.id].orEmpty(), member.id, nicknames) }
            if (groupCandidates.isEmpty()) {
                val miss = groupMissOf(member, participation.progress, generatedAtByQuizSetId[quizSetId])
                return@associate quizSetId to ParticipantMatching(miss = miss)
            }
            quizSetId to ParticipantMatching(groupCandidates = groupCandidates)
        }
    }

    private fun groupMissOf(member: Member, progress: QuizProgress, generatedAt: LocalDateTime?): MatchMiss {
        val prePoolMiss = PrePoolMiss.of(progress, member, generatedAt)
        if (prePoolMiss != null) return prePoolMiss
        if (generatedAt == null) return MatchMiss(MatchMissReason.NOT_GENERATED)
        return MatchMiss(MatchMissReason.NOT_ASSIGNED_TO_GROUP)
    }

    private fun findGroupMatchesInvited(member: Member): List<GroupMatch> {
        val groupMatchIds = groupMatchMemberRepository.findByMemberIdIn(listOf(member.id)).map { it.roomId }
        if (groupMatchIds.isEmpty()) return emptyList()
        return groupMatchRepository.findAllById(groupMatchIds)
    }

    private fun findInvitations(groupMatches: List<GroupMatch>): List<GroupMatchMember> {
        if (groupMatches.isEmpty()) return emptyList()
        return groupMatchMemberRepository.findByRoomIdIn(groupMatches.map { it.id })
    }

    private fun findCandidateGeneratedTimes(quizSetIds: Collection<Long>): Map<Long, LocalDateTime> =
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

class QuizSetParticipation(val quizSet: QuizSet, val progress: QuizProgress)
