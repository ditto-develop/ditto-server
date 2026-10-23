package com.ditto.api.admin.stats.dto

import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.querydsl.GenderBirthDateCount
import com.ditto.domain.member.repository.querydsl.LeaveReasonCount
import com.ditto.domain.member.repository.querydsl.LocationCount
import java.time.LocalDateTime

data class MemberStatsData(
    val countsByStatus: Map<MemberStatus, Long>,
    /** 지금 회차까지 최근 4회차 안에 완주한 회원을 지금 상태로 나눈 수. */
    val currentActiveCountsByStatus: Map<MemberStatus, Long>,
    val recentWeekCount: Int,
    val recentJoinedAts: List<LocalDateTime>,
    val genderBirthDateCounts: List<GenderBirthDateCount>,
    val locationCounts: List<LocationCount>,
    val leaveReasonCounts: List<LeaveReasonCount>,
)
