package com.ditto.api.admin.stats.dto

import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.querydsl.GenderBirthDateCount
import com.ditto.domain.member.repository.querydsl.LeaveReasonCount
import com.ditto.domain.member.repository.querydsl.LocationCount
import java.time.LocalDate
import java.time.LocalDateTime

data class MemberStatsData(
    val countsByStatus: Map<MemberStatus, Long>,
    val currentActiveRoundWeeks: List<LocalDate>,
    val recentlyCompletedActiveStatusCount: Long,
    val recentWeekCount: Int,
    val recentJoinedAts: List<LocalDateTime>,
    val genderBirthDateCounts: List<GenderBirthDateCount>,
    val locationCounts: List<LocationCount>,
    val leaveReasonCounts: List<LeaveReasonCount>,
    val adminMemberCount: Long,
)
