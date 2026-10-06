package com.ditto.api.admin.qa.dto

import java.time.LocalDateTime

/** 이번 주(월 00:00 ~ 다음 주 월 00:00)를 가로 막대로 그린 시각 바로가기. 위치는 주 안의 백분율이다. */
class QaTimeline(
    val nowPercent: Double,
    val days: List<QaTimelineDay>,
    val periods: List<QaTimelinePeriod>,
    val shortcuts: List<QaTimeShortcutOption>,
    val steps: List<QaTimeStepOption>,
) {
    val laneCount: Int = (shortcuts.maxOfOrNull { it.lane } ?: 0) + 1
}

class QaTimelineDay(
    val label: String,
    val startPercent: Double,
)

/** cssClass 는 기간 배지와 같은 색을 쓰려고 둔다. */
class QaTimelinePeriod(
    val label: String,
    val cssClass: String,
    val startPercent: Double,
    val widthPercent: Double,
)

class QaTimeShortcutOption(
    val label: String,
    val note: String?,
    val dateTime: LocalDateTime,
    val positionPercent: Double,
    /** 라벨이 겹치지 않게 나눈 줄 번호. */
    val lane: Int,
    val isPast: Boolean,
    val isCurrent: Boolean,
    val confirmMessage: String?,
    val isIrreversible: Boolean,
)

class QaTimeStepOption(
    val label: String,
    val dateTime: LocalDateTime,
    val confirmMessage: String?,
    val isIrreversible: Boolean,
)
