package com.ditto.api.admin.qa

import com.ditto.api.admin.qa.dto.QaTimeShortcutOption
import com.ditto.api.admin.qa.dto.QaTimeStepOption
import com.ditto.api.admin.qa.dto.QaTimeline
import com.ditto.api.admin.qa.dto.QaTimelineDay
import com.ditto.api.admin.qa.dto.QaTimelinePeriod
import com.ditto.api.system.SystemPeriod
import com.ditto.domain.system.OperationWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToLong

/** 이번 주 타임라인의 위치·줄·확인 문구를 계산한다. 화면은 템플릿이 그린다. */
object QaTimelineComposer {
    /**
     * 라벨 버튼(최대 약 160px)이 막대 폭 1050px 이상에서 겹치지 않는 간격.
     * 막대가 이보다 좁으면 CSS 가 라벨을 목록으로 바꾼다(admin.css 의 qa-week 컨테이너 쿼리).
     */
    private const val MIN_LABEL_GAP_PERCENT = 18.0
    private const val DAYS_IN_WEEK = 7
    private val WEEK_MINUTES = Duration.ofDays(DAYS_IN_WEEK.toLong()).toMinutes().toDouble()
    private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd")
    private val DAY_OF_WEEK_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("E", Locale.KOREAN)

    fun compose(week: OperationWeek, now: LocalDateTime, leadHours: QaChatReminderLeadHours): QaTimeline =
        QaTimeline(
            nowPercent = percentOf(week, now),
            days = composeDays(week),
            periods = composePeriods(week),
            shortcuts = composeShortcuts(week, now, leadHours),
            steps = composeSteps(week, now),
        )

    private fun composeDays(week: OperationWeek): List<QaTimelineDay> =
        (0 until DAYS_IN_WEEK).map { day ->
            val date = week.startedOn.plusDays(day.toLong())
            QaTimelineDay(DATE_FORMATTER.format(date), DAY_OF_WEEK_FORMATTER.format(date), dayPercent(day))
        }

    /** 앱의 요일별 기간은 주 안에서 이어져 있어 기간별로 묶으면 구간이 된다. */
    private fun composePeriods(week: OperationWeek): List<QaTimelinePeriod> =
        (0 until DAYS_IN_WEEK)
            .groupBy { day -> SystemPeriod.from(week.startedOn.plusDays(day.toLong()).dayOfWeek) }
            .map { (period, days) ->
                QaTimelinePeriod(
                    label = "${period.description}(${dayRangeLabel(week, days)})",
                    cssClass = period.name.lowercase(),
                    startPercent = dayPercent(days.first()),
                    widthPercent = dayPercent(days.size),
                )
            }

    /** 알림 시각은 설정에 따라 움직여 선언 순서가 시각 순서가 아닐 수 있다. 시각순으로 놓고 줄을 나눈다. */
    private fun composeShortcuts(
        week: OperationWeek,
        now: LocalDateTime,
        leadHours: QaChatReminderLeadHours,
    ): List<QaTimeShortcutOption> {
        val shortcuts = QaTimeShortcut.entries
            .map { it to it.dateTimeIn(week, leadHours) }
            .sortedBy { (_, dateTime) -> dateTime }
        val lanes = assignLanes(shortcuts.map { (_, dateTime) -> percentOf(week, dateTime) })
        return shortcuts.zip(lanes) { (shortcut, dateTime), lane ->
            val warnings = QaTimeMoveWarning.of(week, now, dateTime)
            QaTimeShortcutOption(
                label = shortcut.label,
                note = shortcut.note,
                dateTime = dateTime,
                positionPercent = percentOf(week, dateTime),
                lane = lane,
                isPast = dateTime < now,
                isCurrent = dateTime == now,
                confirmMessage = QaTimeMoveWarning.confirmMessageOf(warnings),
                isIrreversible = warnings.any { it.isIrreversible },
            )
        }
    }

    private fun composeSteps(week: OperationWeek, now: LocalDateTime): List<QaTimeStepOption> =
        QaTimeStep.entries.map { step ->
            val dateTime = now.plusHours(step.hours)
            val warnings = QaTimeMoveWarning.of(week, now, dateTime)
            QaTimeStepOption(
                label = step.label,
                dateTime = dateTime,
                confirmMessage = QaTimeMoveWarning.confirmMessageOf(warnings),
                isIrreversible = warnings.any { it.isIrreversible },
            )
        }

    /** 시각순 위치마다, 앞 라벨과 충분히 떨어진 가장 위 줄 번호를 준다. */
    internal fun assignLanes(sortedPercents: List<Double>): List<Int> {
        val lastPercentByLane = mutableListOf<Double>()
        return sortedPercents.map { percent ->
            val lane = lastPercentByLane.indexOfFirst { percent - it >= MIN_LABEL_GAP_PERCENT }
            if (lane == -1) {
                lastPercentByLane += percent
                return@map lastPercentByLane.lastIndex
            }
            lastPercentByLane[lane] = percent
            lane
        }
    }

    private fun dayRangeLabel(week: OperationWeek, days: List<Int>): String =
        listOf(days.first(), days.last()).distinct()
            .map { week.startedOn.plusDays(it.toLong()).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN) }
            .joinToString("~")

    /** 주 밖의 시각(다음 주 월 00:01 바로가기 등)은 막대 끝에 붙인다. */
    private fun percentOf(week: OperationWeek, dateTime: LocalDateTime): Double {
        val minutes = Duration.between(week.startedOn.atStartOfDay(), dateTime).toMinutes()
        return roundToHundredths((minutes / WEEK_MINUTES * 100).coerceIn(0.0, 100.0))
    }

    private fun dayPercent(days: Int): Double = roundToHundredths(days * 100.0 / DAYS_IN_WEEK)

    /** 화면 위치에는 소수 둘째 자리면 충분하다. */
    private fun roundToHundredths(value: Double): Double = (value * 100).roundToLong() / 100.0
}
