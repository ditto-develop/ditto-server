package com.ditto.api.admin.stats.dto

import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.querydsl.GenderBirthDateCount
import java.time.LocalDateTime

data class MemberStatsData(
    val countsByStatus: Map<MemberStatus, Long>,
    val recentWeekCount: Int,
    val recentJoinedAts: List<LocalDateTime>,
    val genderBirthDateCounts: List<GenderBirthDateCount>,
)
