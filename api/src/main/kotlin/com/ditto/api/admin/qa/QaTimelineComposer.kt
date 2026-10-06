package com.ditto.api.admin.qa

import com.ditto.api.admin.qa.dto.QaTimeShortcutOption
import com.ditto.api.admin.qa.dto.QaTimeStepOption
import com.ditto.api.admin.qa.dto.QaTimeline
import com.ditto.api.admin.qa.dto.QaTimelineDay
import com.ditto.api.admin.qa.dto.QaTimelinePeriod
import com.ditto.api.system.SystemPeriod
import com.ditto.domain.chat.entity.ChatPeriod
import com.ditto.domain.system.OperationWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 이번 주 시각 바로가기를 타임라인 위치로 바꾼다. 화면이 아니라 계산만 하므로 시각을 받아 순수하게 만든다. */
object QaTimelineComposer {
    /** 라벨 버튼 폭이 주의 이 정도 비율이라, 이보다 가까운 바로가기는 다른 줄에 둔다. */
    private const val MIN_LABEL_GAP_PERCENT = 14.0
    private const val DAYS_IN_WEEK = 7L
    private val WEEK_MINUTES = Duration.ofDays(DAYS_IN_WEEK).toMinutes().toDouble()
    private val DAY_LABEL_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd(E)", Locale.KOREAN)

    fun compose(week: OperationWeek, now: LocalDateTime, leadHours: QaChatReminderLeadHours): QaTimeline {
        val weekStart = week.startedOn.atStartOfDay()
        return QaTimeline(
            nowPercent = percentOf(weekStart, now),
            days = composeDays(week),
            periods = composePeriods(week),
            shortcuts = composeShortcuts(week, now, leadHours),
            steps = composeSteps(week, now),
        )
    }

    private fun composeDays(week: OperationWeek): List<QaTimelineDay> =
        (0 until DAYS_IN_WEEK).map { offset ->
            val day = week.startedOn.plusDays(offset)
            QaTimelineDay(DAY_LABEL_FORMATTER.format(day), dayPercent(offset))
        }

    /** 앱의 요일별 기간을 이어지는 구간으로 묶는다. */
    private fun composePeriods(week: OperationWeek): List<QaTimelinePeriod> {
        val periodByDay = (0 until DAYS_IN_WEEK).map { SystemPeriod.from(week.startedOn.plusDays(it).dayOfWeek) }
        return periodByDay.indices
            .filter { day -> day == 0 || periodByDay[day] != periodByDay[day - 1] }
            .map { startDay ->
                val dayCount = (startDay until periodByDay.size)
                    .takeWhile { periodByDay[it] == periodByDay[startDay] }
                    .count()
                QaTimelinePeriod(periodByDay[startDay], dayPercent(startDay.toLong()), dayPercent(dayCount.toLong()))
            }
    }

    private fun composeShortcuts(
        week: OperationWeek,
        now: LocalDateTime,
        leadHours: QaChatReminderLeadHours,
    ): List<QaTimeShortcutOption> {
        val weekStart = week.startedOn.atStartOfDay()
        val lastPercentByLane = mutableListOf<Double>()
        return QaTimeShortcut.entries.map { shortcut ->
            val dateTime = shortcut.dateTimeIn(week, leadHours)
            val percent = percentOf(weekStart, dateTime)
            QaTimeShortcutOption(
                label = shortcut.label,
                dateTime = dateTime,
                confirmMessage = shortcut.confirmMessage,
                percent = percent,
                lane = assignLane(lastPercentByLane, percent),
                isPast = dateTime <= now,
            )
        }
    }

    /** 앞 라벨과 충분히 떨어진 가장 위 줄에 둔다. 바로가기는 시각 순서라 줄마다 마지막 위치만 보면 된다. */
    private fun assignLane(lastPercentByLane: MutableList<Double>, percent: Double): Int {
        val lane = lastPercentByLane.indexOfFirst { percent - it >= MIN_LABEL_GAP_PERCENT }
        if (lane == -1) {
            lastPercentByLane += percent
            return lastPercentByLane.lastIndex
        }
        lastPercentByLane[lane] = percent
        return lane
    }

    /** 이번 주 채팅 마감을 넘기는 이동이면 되돌릴 수 없으니 확인받는다. */
    private fun composeSteps(week: OperationWeek, now: LocalDateTime): List<QaTimeStepOption> {
        val chatExpiresAt = ChatPeriod.weekendOf(week.startedOn.atStartOfDay()).expiresAt
        return QaTimeStep.entries.map { step ->
            val dateTime = now.plusHours(step.hours)
            val crossesChatEnd = now < chatExpiresAt && dateTime >= chatExpiresAt
            QaTimeStepOption(step.label, dateTime, CHAT_END_CONFIRM_MESSAGE.takeIf { crossesChatEnd })
        }
    }

    private fun percentOf(weekStart: LocalDateTime, dateTime: LocalDateTime): Double {
        val minutes = Duration.between(weekStart, dateTime).toMinutes()
        return (minutes / WEEK_MINUTES * 100).coerceIn(0.0, 100.0)
    }

    private fun dayPercent(days: Long): Double = days * 100.0 / DAYS_IN_WEEK
}
