package com.ditto.api.admin.stats.dto

import com.ditto.domain.match.repository.querydsl.ActiveGroupMemberCount
import com.ditto.domain.match.repository.querydsl.GroupMatchMemberCount
import com.ditto.domain.match.repository.querydsl.GroupMatchScore
import com.ditto.domain.match.repository.querydsl.MatchCandidateCount
import com.ditto.domain.match.repository.querydsl.PersonalMatchMemberCount
import com.ditto.domain.match.repository.querydsl.PersonalMatchStatusCount
import com.ditto.domain.match.repository.querydsl.SecretCandidateOwnerCount
import com.ditto.domain.quiz.repository.querydsl.UnmatchedMember
import java.time.LocalDate

data class MatchStatsData(
    val matchCandidateCounts: List<MatchCandidateCount>,
    val candidateCountsPerOwner: List<Long>,
    val personalMatchMemberCounts: List<PersonalMatchMemberCount>,
    val personalMatchRequesterCounts: List<PersonalMatchMemberCount>,
    val personalMatchReceiverCounts: List<PersonalMatchMemberCount>,
    val personalMatchAcceptedReceiverCounts: List<PersonalMatchMemberCount>,
    val personalMatchStatusCounts: List<PersonalMatchStatusCount>,
    val groupMatchMemberCounts: List<GroupMatchMemberCount>,
    val pairScores: List<Double>,
    val acceptedPairScores: List<Double?>,
    val groupScores: List<GroupMatchScore>,
    val activeGroupMemberCounts: List<ActiveGroupMemberCount>,
    val unmatchedRoundWeeks: List<LocalDate>,
    val membersUnmatchedInEveryRound: List<UnmatchedMember>,
    val secretMatching: SecretMatchingStats,
)

/** 시크릿 매칭(IMP-2). 위 지표는 정규 매칭만 세서 이 숫자는 따로 본다. */
data class SecretMatchingStats(
    val lateCompletedMemberCount: Long,
    val candidateOwnerCounts: List<SecretCandidateOwnerCount>,
    val acceptedPairCount: Long,
)
