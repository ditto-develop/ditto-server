package com.ditto.api.admin.quiz

import com.ditto.api.admin.quiz.dto.GroupCandidate
import com.ditto.api.admin.quiz.dto.MatchMissReason
import com.ditto.api.admin.quiz.dto.OneToOneRecords
import com.ditto.api.admin.quiz.dto.ParticipantMatching
import com.ditto.api.admin.quiz.dto.QuizSetMatching
import com.ditto.domain.match.entity.GroupMatch
import com.ditto.domain.match.entity.GroupMatchMember
import com.ditto.domain.match.entity.MatchCandidate
import com.ditto.domain.match.entity.PersonalMatch
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QuizProgress
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 참여 현황 화면용으로 퀴즈셋의 저장된 매칭 후보와 1:1 신청을 참여자별로 모은다.
 * 1:1 후보가 없는 이유는 [OneToOneMissFinder]가 정한다.
 */
@Component
class AdminParticipantMatchingReader(
    private val matchCandidateRepository: MatchCandidateRepository,
    private val personalMatchRepository: PersonalMatchRepository,
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    private val memberRepository: MemberRepository,
    private val oneToOneMissFinder: OneToOneMissFinder,
) {
    fun read(source: MatchingReadSource): QuizSetMatching = when (source.quizSet.matchingType) {
        MatchingType.ONE_TO_ONE -> readOneToOne(source)
        MatchingType.GROUP -> readGroup(source)
    }

    private fun readOneToOne(source: MatchingReadSource): QuizSetMatching {
        val candidatesByOwnerId = findCandidates(source).groupBy { it.ownerMemberId }
        val generatedAt = candidatesByOwnerId.values.flatten().minOfOrNull { it.createdAt }
        val personalMatches = personalMatchRepository.findByQuizSetIdIn(listOf(source.quizSet.id))
        val nicknames = loadNicknames(
            source,
            partnerIds = candidatesByOwnerId.values.flatten().map { it.otherMemberId } +
                personalMatches.flatMap { listOf(it.memberId1, it.memberId2) },
        )
        val missByMemberId = oneToOneMissFinder.findMisses(
            OneToOneMissInput(
                source = source,
                candidateOwnerIds = candidatesByOwnerId.keys,
                acceptedMemberIds = acceptedMemberIdsOf(personalMatches),
                generatedAt = generatedAt,
            ),
        )

        val byMemberId = source.memberIds.associateWith { memberId ->
            val requests = personalMatches.filter { memberId in setOf(it.memberId1, it.memberId2) }
            val records = OneToOneRecords(memberId, candidatesByOwnerId[memberId].orEmpty(), requests)
            ParticipantMatching.ofOneToOne(records, nicknames, missByMemberId[memberId])
        }
        return QuizSetMatching(generatedAt, byMemberId)
    }

    private fun readGroup(source: MatchingReadSource): QuizSetMatching {
        val groupMatches = groupMatchRepository.findByQuizSetId(source.quizSet.id)
        val generatedAt = groupMatches.minOfOrNull { it.createdAt }
        val invitationsByGroupMatchId = findInvitations(groupMatches).groupBy { it.roomId }
        val rooms = groupMatches.map { GroupRoom(it, invitationsByGroupMatchId[it.id].orEmpty()) }
        val invitedMemberIds = rooms.flatMap { room -> room.invitations.map { it.memberId } }
        val nicknames = loadNicknames(source, partnerIds = invitedMemberIds)

        val byMemberId = source.progresses.associate { progress ->
            val myRooms = rooms.filter { room -> room.invitations.any { it.memberId == progress.memberId } }
            progress.memberId to groupMatchingOf(progress, myRooms, GroupReadContext(source, nicknames, generatedAt))
        }
        return QuizSetMatching(generatedAt, byMemberId)
    }

    // 지금 배치는 한 사람을 한 그룹에만 넣지만(ADR 0033) 그 전에 겹쳐 만든 그룹도 남아 있어 모두 보여 준다.
    private fun groupMatchingOf(
        progress: QuizProgress,
        myRooms: List<GroupRoom>,
        context: GroupReadContext,
    ): ParticipantMatching {
        if (myRooms.isNotEmpty()) {
            val groupCandidates = myRooms.map { room ->
                GroupCandidate.of(room.groupMatch, room.invitations, progress.memberId, context.nicknames)
            }
            return ParticipantMatching(groupCandidates = groupCandidates)
        }
        val member = context.source.membersById[progress.memberId]
        val prePoolMiss = PrePoolMiss.of(progress, member, context.generatedAt)
        if (prePoolMiss != null) return ParticipantMatching(missReason = prePoolMiss)

        if (context.generatedAt == null) return ParticipantMatching(missReason = MatchMissReason.NOT_GENERATED)
        return ParticipantMatching(missReason = MatchMissReason.NOT_ASSIGNED_TO_GROUP)
    }

    private fun acceptedMemberIdsOf(personalMatches: List<PersonalMatch>): Set<Long> =
        personalMatches
            .filter { it.status == PersonalMatchStatus.ACCEPTED }
            .flatMap { listOf(it.memberId1, it.memberId2) }
            .toSet()

    // 상대가 이 퀴즈셋의 진행 기록이 없으면 참여자 회원 맵에 없으므로 따로 읽는다.
    private fun loadNicknames(source: MatchingReadSource, partnerIds: List<Long>): Map<Long, String> {
        val missingIds = partnerIds.toSet() - source.membersById.keys
        val missingMembers = if (missingIds.isEmpty()) emptyList() else memberRepository.findAllById(missingIds)
        return (source.membersById.values + missingMembers).associate { it.id to it.nickname }
    }

    private fun findCandidates(source: MatchingReadSource): List<MatchCandidate> {
        if (source.memberIds.isEmpty()) return emptyList()
        return matchCandidateRepository.findByQuizSetIdInAndOwnerMemberIdIn(listOf(source.quizSet.id), source.memberIds)
    }

    private fun findInvitations(groupMatches: List<GroupMatch>): List<GroupMatchMember> {
        if (groupMatches.isEmpty()) return emptyList()
        return groupMatchMemberRepository.findByRoomIdIn(groupMatches.map { it.id })
    }

    private class GroupRoom(val groupMatch: GroupMatch, val invitations: List<GroupMatchMember>)

    private class GroupReadContext(
        val source: MatchingReadSource,
        val nicknames: Map<Long, String>,
        val generatedAt: LocalDateTime?,
    )
}
