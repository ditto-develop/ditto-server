package com.ditto.api.admin.quiz

import com.ditto.api.admin.quiz.dto.GroupCandidate
import com.ditto.api.admin.quiz.dto.GroupCandidateMember
import com.ditto.api.admin.quiz.dto.MatchMiss
import com.ditto.api.admin.quiz.dto.MatchMissReason
import com.ditto.api.admin.quiz.dto.ParticipantMatching
import com.ditto.api.admin.quiz.dto.PersonalCandidate
import com.ditto.api.admin.quiz.dto.PersonalRequestState
import com.ditto.api.admin.quiz.dto.QuizSetMatching
import com.ditto.domain.match.entity.GroupMatch
import com.ditto.domain.match.entity.GroupMatchMember
import com.ditto.domain.match.entity.MatchCandidate
import com.ditto.domain.match.entity.PersonalMatch
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.member.entity.Member
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QuizProgress
import com.ditto.domain.quiz.entity.QuizProgressStatus
import com.ditto.domain.quiz.entity.QuizSet
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/** 참여 현황 화면용으로 퀴즈셋의 저장된 매칭 후보를 참여자별로 모으고, 후보가 없는 참여자의 이유를 붙인다. */
@Component
class AdminParticipantMatchingReader(
    private val matchCandidateRepository: MatchCandidateRepository,
    private val personalMatchRepository: PersonalMatchRepository,
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    private val oneToOneMissReasonFinder: OneToOneMissReasonFinder,
) {
    fun read(source: MatchingReadSource): QuizSetMatching = when (source.quizSet.matchingType) {
        MatchingType.ONE_TO_ONE -> readOneToOne(source)
        MatchingType.GROUP -> readGroup(source)
    }

    private fun readOneToOne(source: MatchingReadSource): QuizSetMatching {
        val quizSetId = source.quizSet.id
        val candidatesByOwnerId = findCandidates(quizSetId, source.memberIds).groupBy { it.ownerMemberId }
        val generatedAt = candidatesByOwnerId.values.flatten().minOfOrNull { it.createdAt }
        val personalMatchByPair = personalMatchRepository.findByQuizSetIdIn(listOf(quizSetId))
            .associateBy { setOf(it.memberId1, it.memberId2) }

        val withoutCandidate = source.progresses.filter { it.memberId !in candidatesByOwnerId }
        val missByMemberId = findOneToOneMisses(source, withoutCandidate, generatedAt)
        val byMemberId = source.memberIds.associateWith { memberId ->
            ParticipantMatching(
                personalCandidates = candidatesByOwnerId[memberId].orEmpty()
                    .sortedByDescending { it.score }
                    .map { candidate -> toPersonalCandidate(candidate, source, personalMatchByPair) },
                miss = missByMemberId[memberId],
            )
        }
        return QuizSetMatching(generatedAt, byMemberId)
    }

    private fun readGroup(source: MatchingReadSource): QuizSetMatching {
        val groupMatches = groupMatchRepository.findByQuizSetId(source.quizSet.id)
        val generatedAt = groupMatches.minOfOrNull { it.createdAt }
        val invitationsByGroupMatchId = findInvitations(groupMatches).groupBy { it.roomId }
        // 배치는 한 사람을 한 그룹에만 넣는다(ADR 0033).
        val groupRoomByMemberId = groupMatches
            .map { GroupRoom(it, invitationsByGroupMatchId[it.id].orEmpty()) }
            .flatMap { room -> room.invitations.map { invitation -> invitation.memberId to room } }
            .toMap()

        val byMemberId = source.progresses.associate { progress ->
            val room = groupRoomByMemberId[progress.memberId]
            val matching = if (room == null) {
                val miss = preliminaryMissOf(progress, source.membersById[progress.memberId], generatedAt)
                ParticipantMatching(miss = miss ?: MatchMiss(MatchMissReason.NOT_ASSIGNED_TO_GROUP))
            } else {
                ParticipantMatching(groupCandidate = toGroupCandidate(room, progress.memberId, source))
            }
            progress.memberId to matching
        }
        return QuizSetMatching(generatedAt, byMemberId)
    }

    /** 풀에 들기 전 단계에서 빠진 이유. 풀 이후 단계는 매칭 타입마다 다르게 본다. */
    private fun preliminaryMissOf(progress: QuizProgress, member: Member?, generatedAt: LocalDateTime?): MatchMiss? =
        when {
            member == null -> MatchMiss(MatchMissReason.MEMBER_DELETED)
            progress.status != QuizProgressStatus.COMPLETED -> MatchMiss(MatchMissReason.NOT_COMPLETED)
            generatedAt != null && progress.updatedAt.isAfter(generatedAt) ->
                MatchMiss(MatchMissReason.COMPLETED_AFTER_GENERATION)
            else -> null
        }

    private fun findOneToOneMisses(
        source: MatchingReadSource,
        withoutCandidate: List<QuizProgress>,
        generatedAt: LocalDateTime?,
    ): Map<Long, MatchMiss> {
        val preliminaryMissByMemberId = withoutCandidate.mapNotNull { progress ->
            preliminaryMissOf(progress, source.membersById[progress.memberId], generatedAt)?.let { progress.memberId to it }
        }.toMap()
        val poolCandidateIds = withoutCandidate.map { it.memberId }.toSet() - preliminaryMissByMemberId.keys
        val poolMissByMemberId = oneToOneMissReasonFinder.findPoolMisses(source.quizSet.id, poolCandidateIds)
        val exclusionMissByMemberId = (poolCandidateIds - poolMissByMemberId.keys).associateWith { memberId ->
            exclusionMissOf(source.membersById.getValue(memberId))
        }
        return preliminaryMissByMemberId + poolMissByMemberId + exclusionMissByMemberId
    }

    // 1:1 제외 정책(OneToOneExclusionPolicy)은 비활성과 이미 성사 두 가지만 본다.
    private fun exclusionMissOf(member: Member): MatchMiss =
        if (member.isActive()) MatchMiss(MatchMissReason.EXCLUDED_ALREADY_MATCHED) else MatchMiss(MatchMissReason.EXCLUDED_INACTIVE)

    private fun toPersonalCandidate(
        candidate: MatchCandidate,
        source: MatchingReadSource,
        personalMatchByPair: Map<Set<Long>, PersonalMatch>,
    ): PersonalCandidate =
        PersonalCandidate(
            otherMemberId = candidate.otherMemberId,
            otherNickname = source.membersById[candidate.otherMemberId]?.nickname,
            score = candidate.score,
            matchedQuestionCount = candidate.matchedQuestionCount,
            totalQuestionCount = candidate.totalQuestionCount,
            requestState = personalMatchByPair[setOf(candidate.ownerMemberId, candidate.otherMemberId)]
                ?.let { PersonalRequestState.of(it, viewerId = candidate.ownerMemberId) },
        )

    private fun toGroupCandidate(room: GroupRoom, memberId: Long, source: MatchingReadSource): GroupCandidate {
        val (myInvitations, otherInvitations) = room.invitations.partition { it.memberId == memberId }
        return GroupCandidate(
            groupMatchId = room.groupMatch.id,
            score = room.groupMatch.score,
            isFormed = room.groupMatch.isActive,
            myStatus = myInvitations.single().status,
            otherMembers = otherInvitations.map { invitation ->
                GroupCandidateMember(invitation.memberId, source.membersById[invitation.memberId]?.nickname, invitation.status)
            },
        )
    }

    private fun findCandidates(quizSetId: Long, memberIds: List<Long>): List<MatchCandidate> {
        if (memberIds.isEmpty()) return emptyList()
        return matchCandidateRepository.findByQuizSetIdInAndOwnerMemberIdIn(listOf(quizSetId), memberIds)
    }

    private fun findInvitations(groupMatches: List<GroupMatch>): List<GroupMatchMember> {
        if (groupMatches.isEmpty()) return emptyList()
        return groupMatchMemberRepository.findByRoomIdIn(groupMatches.map { it.id })
    }

    private class GroupRoom(val groupMatch: GroupMatch, val invitations: List<GroupMatchMember>)
}

class MatchingReadSource(
    val quizSet: QuizSet,
    val progresses: List<QuizProgress>,
    val membersById: Map<Long, Member>,
) {
    val memberIds: List<Long> = progresses.map { it.memberId }
}
