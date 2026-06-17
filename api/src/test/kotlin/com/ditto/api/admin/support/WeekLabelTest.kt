package com.ditto.api.admin.support

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith

class WeekLabelTest : FreeSpec({
    val weekLabel = WeekLabel()

    "2026년 6월은 5주차까지 있다 (6/1 월요일 시작)" {
        weekLabel.totalWeeks(2026, 6) shouldBe 5
    }

    "of 는 'yyyy-MM 현재/총주차' 형식으로 만든다" {
        weekLabel.of(2026, 6, 3) shouldBe "2026-06 3/5주차"
    }

    "월은 2자리로 0 패딩한다" {
        weekLabel.of(2026, 1, 2) shouldStartWith "2026-01 2/"
    }
})
