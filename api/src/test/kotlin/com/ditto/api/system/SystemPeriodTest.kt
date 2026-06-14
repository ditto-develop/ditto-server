package com.ditto.api.system

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.time.DayOfWeek

class SystemPeriodTest : FreeSpec({
    "월~수는 QUIZ_PERIOD" {
        SystemPeriod.from(DayOfWeek.MONDAY) shouldBe SystemPeriod.QUIZ_PERIOD
        SystemPeriod.from(DayOfWeek.TUESDAY) shouldBe SystemPeriod.QUIZ_PERIOD
        SystemPeriod.from(DayOfWeek.WEDNESDAY) shouldBe SystemPeriod.QUIZ_PERIOD
    }

    "목요일은 MATCHING_PERIOD" {
        SystemPeriod.from(DayOfWeek.THURSDAY) shouldBe SystemPeriod.MATCHING_PERIOD
    }

    "금~일은 CHATTING_PERIOD" {
        SystemPeriod.from(DayOfWeek.FRIDAY) shouldBe SystemPeriod.CHATTING_PERIOD
        SystemPeriod.from(DayOfWeek.SATURDAY) shouldBe SystemPeriod.CHATTING_PERIOD
        SystemPeriod.from(DayOfWeek.SUNDAY) shouldBe SystemPeriod.CHATTING_PERIOD
    }
})
