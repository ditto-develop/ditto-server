package com.ditto.api.admin.qa.dto

import com.ditto.api.system.SystemPeriod
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

class QaTimelinePeriod(
    val period: SystemPeriod,
    val startPercent: Double,
    val widthPercent: Double,
)

/** 라벨이 겹치지 않게 lane(줄)을 나눠 받는다. */
class QaTimeShortcutOption(
    val label: String,
    val dateTime: LocalDateTime,
    val confirmMessage: String?,
    val percent: Double,
    val lane: Int,
    val isPast: Boolean,
)

class QaTimeStepOption(
    val label: String,
    val dateTime: LocalDateTime,
    val confirmMessage: String?,
)
