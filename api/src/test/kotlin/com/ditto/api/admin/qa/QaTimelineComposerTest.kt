package com.ditto.api.admin.qa

import com.ditto.api.system.SystemPeriod
import com.ditto.domain.system.OperationWeek
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class QaTimelineComposerTest : FreeSpec({

    val monday = LocalDate.of(2026, 10, 5)
    val week = OperationWeek(monday)
    val leadHours = QaChatReminderLeadHours(noMessage = 12, endingSoon = 6)

    fun compose(nowDaysAfterMonday: Long, hour: Int = 12) =
        QaTimelineComposer.compose(week, monday.plusDays(nowDaysAfterMonday).atTime(hour, 0), leadHours)

    "바로가기는 앱의 기간 정의와 알림 리드 시간으로 시각을 잡는다" {
        compose(nowDaysAfterMonday = 1).shortcuts.map { it.dateTime } shouldBe listOf(
            monday.atTime(0, 1),
            monday.plusDays(2).atTime(23, 49, 59),
            monday.plusDays(3).atTime(0, 1),
            monday.plusDays(3).atTime(23, 50),
            monday.plusDays(4).atTime(0, 1),
            monday.plusDays(4).atTime(12, 1),
            monday.plusDays(6).atTime(18, 1),
            monday.plusDays(7).atTime(0, 1),
        )
    }

    "가까운 바로가기는 라벨이 겹치지 않게 다른 줄에 둔다" {
        val shortcuts = compose(nowDaysAfterMonday = 1).shortcuts

        shortcuts.map { it.lane } shouldBe listOf(0, 0, 1, 0, 1, 2, 0, 1)
    }

    "지금보다 앞선 바로가기는 지난 것으로 표시한다" {
        compose(nowDaysAfterMonday = 3).shortcuts.map { it.isPast } shouldBe
            listOf(true, true, true, false, false, false, false, false)
    }

    "주 안의 위치를 백분율로 준다" {
        val timeline = compose(nowDaysAfterMonday = 3, hour = 12)

        timeline.nowPercent shouldBe (3.5 / 7 * 100 plusOrMinus 0.01)
        timeline.shortcuts.last().percent shouldBe 100.0
    }

    "앱의 요일별 기간을 이어지는 구간으로 묶는다" {
        val periods = compose(nowDaysAfterMonday = 1).periods

        periods.map { Triple(it.period, it.startPercent, it.widthPercent) } shouldBe listOf(
            Triple(SystemPeriod.QUIZ_PERIOD, 0.0, 300.0 / 7),
            Triple(SystemPeriod.MATCHING_PERIOD, 300.0 / 7, 100.0 / 7),
            Triple(SystemPeriod.CHATTING_PERIOD, 400.0 / 7, 300.0 / 7),
        )
    }

    "채팅 마감을 넘기는 상대 이동만 확인받는다" {
        val sundayNight = QaTimelineComposer.compose(week, monday.plusDays(6).atTime(23, 30), leadHours)

        sundayNight.steps.map { it.confirmMessage != null } shouldBe listOf(true, true)
        compose(nowDaysAfterMonday = 1).steps.map { it.confirmMessage != null } shouldBe listOf(false, false)
    }
})
