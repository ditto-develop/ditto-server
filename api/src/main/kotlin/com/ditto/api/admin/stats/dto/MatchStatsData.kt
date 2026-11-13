package com.ditto.api.admin.stats.dto

import com.ditto.domain.match.repository.querydsl.ActiveGroupMemberCount
import com.ditto.domain.match.repository.querydsl.GroupMatchMemberCount
import com.ditto.domain.match.repository.querydsl.GroupMatchScore
import com.ditto.domain.match.repository.querydsl.MatchCandidateCount
import com.ditto.domain.match.repository.querydsl.PersonalMatchMemberCount
import com.ditto.domain.match.repository.querydsl.PersonalMatchStatusCount
import com.ditto.domain.quiz.repository.querydsl.UnmatchedMember
import java.time.LocalDate

data class MatchStatsData(
    val matchCandidateCounts: List<MatchCandidateCount>,
    val candidateCountsPerOwner: List<Long>,
    val personalMatchMemberCounts: List<PersonalMatchMemberCount>,
    val personalMatchRequesterCounts: List<PersonalMatchMemberCount>,
    val personalMatchReceiverCounts: List<PersonalMatchMemberCount>,
    val personalMatchAcceptedReceiverCounts: List<PersonalMatchMemberCount>,
    val personalMatchRequestCounts: List<PersonalMatchStatusCount>,
    val groupMatchMemberCounts: List<GroupMatchMemberCount>,
    val pairScores: List<Double>,
    val acceptedPairScores: List<Double?>,
    val groupScores: List<GroupMatchScore>,
    val activeGroupMemberCounts: List<ActiveGroupMemberCount>,
    val unmatchedRoundWeeks: List<LocalDate>,
    val membersUnmatchedInEveryRound: List<UnmatchedMember>,
)
