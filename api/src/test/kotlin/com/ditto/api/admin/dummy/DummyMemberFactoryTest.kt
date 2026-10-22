package com.ditto.api.admin.dummy

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.Period

class DummyMemberFactoryTest : FreeSpec(
    {
        "randomBirthDateOf" - {
            listOf(LocalDate.of(2026, 10, 22), LocalDate.of(2028, 2, 29), LocalDate.of(2027, 3, 1)).forEach { today ->
                "$today 에 만 나이가 고른 나이인 날만 나온다" {
                    listOf(20, 23, 31, 100).forEach { age ->
                        repeat(200) {
                            val birthDate = DummyMemberFactory.randomBirthDateOf(age, today)

                            Period.between(birthDate, today).years shouldBe age
                        }
                    }
                }
            }
        }
    },
)
