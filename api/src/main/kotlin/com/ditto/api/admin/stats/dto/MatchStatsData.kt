package com.ditto.api.admin.stats.dto

import com.ditto.domain.match.repository.querydsl.GroupMatchMemberCount
import com.ditto.domain.match.repository.querydsl.MatchCandidateCount
import com.ditto.domain.match.repository.querydsl.PersonalMatchMemberCount

data class MatchStatsData(
    val matchCandidateCounts: List<MatchCandidateCount>,
    val personalMatchMemberCounts: List<PersonalMatchMemberCount>,
    val groupMatchMemberCounts: List<GroupMatchMemberCount>,
)
