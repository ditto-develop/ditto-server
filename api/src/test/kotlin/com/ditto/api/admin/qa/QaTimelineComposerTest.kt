package com.ditto.api.admin.qa

import com.ditto.api.admin.qa.dto.QaTimeline
import com.ditto.domain.system.OperationWeek
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeSortedBy
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.LocalDate
import java.time.LocalDateTime

class QaTimelineComposerTest : FreeSpec({

    val monday = LocalDate.of(2026, 10, 5)
    val week = OperationWeek(monday)
    val defaultLeadHours = QaChatReminderLeadHours(firstMessageHours = 12, endingSoonHours = 6)

    fun at(daysAfterMonday: Long, hour: Int, minute: Int = 0): LocalDateTime =
        monday.plusDays(daysAfterMonday).atTime(hour, minute)

    fun compose(now: LocalDateTime, leadHours: QaChatReminderLeadHours = defaultLeadHours): QaTimeline =
        QaTimelineComposer.compose(week, now, leadHours)

    fun QaTimeline.shortcut(label: String) = shortcuts.first { it.label == label }

    fun QaTimeline.step(label: String) = steps.first { it.label == label }

    "바로가기 시각은 앱의 기간과 알림 설정을 따른다" {
        compose(at(1, 12)).shortcuts.associate { it.label to it.dateTime } shouldBe mapOf(
            "퀴즈 열린 직후" to at(0, 0, 1),
            "퀴즈 마감 직전" to monday.plusDays(2).atTime(23, 49, 59),
            "퀴즈 마감 직후" to at(3, 0, 1),
            "그룹 응답 마감 직전" to at(3, 23, 50),
            "채팅방 열린 직후" to at(4, 0, 1),
            "첫 인사 알림 직후" to at(4, 12, 1),
            "마감 임박 알림 직후" to at(6, 18, 1),
            "채팅 마감 직후" to at(7, 0, 1),
        )
    }

    "알림 설정으로 순서가 바뀌어도 시각순으로 놓는다" {
        val timeline = compose(at(1, 12), QaChatReminderLeadHours(firstMessageHours = 12, endingSoonHours = 60))

        timeline.shortcuts shouldBeSortedBy { it.dateTime }
        timeline.shortcut("마감 임박 알림 직후").dateTime shouldBe at(4, 12, 1)
    }

    "같은 줄의 라벨은 최소 간격 이상 떨어진다" {
        val shiftedLeadHours = QaChatReminderLeadHours(firstMessageHours = 30, endingSoonHours = 60)

        listOf(defaultLeadHours, shiftedLeadHours).forEach { leadHours ->
            compose(at(1, 12), leadHours).shortcuts.groupBy { it.lane }.values.forEach { sameLane ->
                sameLane.zipWithNext { left, right ->
                    right.positionPercent - left.positionPercent shouldBeGreaterThanOrEqual 18.0
                }
            }
        }
    }

    "가까운 위치는 다른 줄로, 충분히 떨어지면 위 줄로 돌아간다" {
        QaTimelineComposer.assignLanes(listOf(0.0, 10.0, 20.0, 30.0, 60.0)) shouldBe listOf(0, 1, 0, 1, 0)
    }

    "지금 시각과 같은 바로가기는 현재로, 앞선 것은 지난 것으로 표시한다" {
        val timeline = compose(at(3, 0, 1))

        timeline.shortcuts.filter { it.isCurrent }.map { it.label } shouldBe listOf("퀴즈 마감 직후")
        timeline.shortcuts.filter { it.isPast }.map { it.label } shouldBe listOf("퀴즈 열린 직후", "퀴즈 마감 직전")
    }

    "목요일 정오는 주의 절반 위치다" {
        compose(at(3, 12)).nowPercent shouldBe 50.0
    }

    "앱의 요일별 기간을 이름과 요일 범위가 붙은 구간으로 묶는다" {
        val periods = compose(at(1, 12)).periods

        periods.map { listOf(it.label, it.cssClass, it.startPercent, it.widthPercent) } shouldBe listOf(
            listOf("퀴즈(월~수)", "quiz_period", 0.0, 42.86),
            listOf("매칭(목)", "matching_period", 42.86, 14.29),
            listOf("채팅(금~일)", "chatting_period", 57.14, 42.86),
        )
    }

    "날짜 눈금은 월요일부터 하루씩 놓는다" {
        val monday = compose(at(1, 12)).days.first()

        monday.date shouldBe "10/05"
        monday.dayOfWeek shouldBe "월"
    }

    "확인 창" - {
        "마감을 넘기지 않는 이동은 확인받지 않는다" {
            val timeline = compose(at(1, 12))

            timeline.steps.map { it.confirmMessage }.forEach { it.shouldBeNull() }
            timeline.shortcut("그룹 응답 마감 직전").confirmMessage.shouldBeNull()
        }

        "그룹 응답 마감을 넘기면 되돌릴 수 없다고 확인받는다" {
            val step = compose(at(3, 23, 30)).step("+1시간")

            step.confirmMessage.shouldNotBeNull() shouldContain "그룹 응답 마감을 넘깁니다"
            step.isIrreversible shouldBe true
        }

        "채팅 마감을 넘기면 되돌릴 수 없다고 확인받는다" {
            val step = compose(at(6, 23, 30)).step("+1시간")

            step.confirmMessage.shouldNotBeNull() shouldContain "채팅 마감을 넘깁니다"
            step.isIrreversible shouldBe true
        }

        "두 마감을 한 번에 넘기면 둘 다 알린다" {
            val message = compose(at(1, 12)).shortcut("채팅 마감 직후").confirmMessage.shouldNotBeNull()

            message shouldContain "그룹 응답 마감을 넘깁니다"
            message shouldContain "채팅 마감을 넘깁니다"
            message shouldContain "되돌릴 수 없습니다."
        }

        "지난 시점으로 돌아가면 이미 일어난 일은 그대로라고 확인받는다" {
            val shortcut = compose(at(4, 12)).shortcut("퀴즈 열린 직후")

            shortcut.confirmMessage.shouldNotBeNull() shouldContain "지난 시점으로 돌아갑니다"
            shortcut.isIrreversible shouldBe false
        }
    }
})
