package com.ditto.api.admin.support

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class WeekLabelTest : FreeSpec({
    val weekLabel = WeekLabel()

    "of 는 월요일~일요일 날짜 범위 형식으로 만든다" {
        weekLabel.of(LocalDate.of(2026, 7, 27)) shouldBe "2026.07.27 ~ 2026.08.02"
    }

    "연 경계에 걸친 주는 연도가 넘어간 종료일을 보여준다" {
        weekLabel.of(LocalDate.of(2026, 12, 28)) shouldBe "2026.12.28 ~ 2027.01.03"
    }
})
