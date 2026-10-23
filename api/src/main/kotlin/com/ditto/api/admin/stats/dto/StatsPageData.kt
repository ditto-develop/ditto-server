package com.ditto.api.admin.stats.dto

import com.ditto.api.admin.stats.AgeBand
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.memberreport.entity.MemberReportReason
import com.ditto.domain.memberreport.entity.MemberReportStatus
import com.ditto.domain.review.entity.MeetingStatus
import com.ditto.domain.review.entity.ReviewAnswerContent
import java.time.LocalDate

/** stats.js 가 읽는 페이지 데이터. 비율·구간 묶기는 JS 가 한다. */
data class StatsPageData(
    val weekStartedOn: LocalDate,
    val today: LocalDate,
    val memberStats: MemberStatsData,
    val quizStats: QuizStatsData,
    val matchStats: MatchStatsData,
    val memberReviewStats: MemberReviewStatsData,
    val retentionStats: RetentionStatsData,
    val memberReportStats: MemberReportStatsData,
    val genderLabels: Map<String, String> = Gender.entries.associate { it.name to it.description },
    val quizMatchingTypeLabels: Map<String, String>,
    val memberReviewMatchTypeLabels: Map<String, String>,
    val meetingStatusLabels: Map<String, String> = MeetingStatus.entries.associate { it.name to it.description },
    val ratingScores: List<Int> = (ReviewAnswerContent.RATING_MAX downTo ReviewAnswerContent.RATING_MIN).toList(),
    val memberReportReasonLabels: Map<String, String> =
        MemberReportReason.entries.associate { it.name to it.description },
    val memberReportStatusLabels: Map<String, String> =
        MemberReportStatus.entries.associate { it.name to it.description },
    val ageBandRanges: List<AgeBandRange> = AgeBand.entries.map { it.toRange() },
)

data class AgeBandRange(
    val label: String,
    val minAge: Int,
    val maxAge: Int,
)
