package com.ditto.api.admin.stats

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class AgeBandTest : FreeSpec(
    {
        "birthDateRange" - {
            "오늘 만 23세 마지막 날인 사람부터 오늘 만 20세가 된 사람까지다" {
                val range = AgeBand.EARLY_20S.birthDateRange(LocalDate.of(2026, 10, 22))

                range.start shouldBe LocalDate.of(2002, 10, 23)
                range.endInclusive shouldBe LocalDate.of(2006, 10, 22)
            }
        }

        "toRange" - {
            "화면 JS 가 쓰는 라벨과 나이 경계를 담는다" {
                val range = AgeBand.THIRTIES.toRange()

                range.label shouldBe "30대"
                range.minAge shouldBe 30
                range.maxAge shouldBe 39
            }
        }
    },
)
