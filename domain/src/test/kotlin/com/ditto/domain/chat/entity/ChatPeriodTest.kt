package com.ditto.domain.chat.entity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 순수 계산이라 통합 테스트가 아닌 단위 테스트로 둔다.
 * 기준 주: 2026-03-09(월) ~ 2026-03-15(일), 금요일은 2026-03-13.
 */
class ChatPeriodTest : FreeSpec({

    "weekendOf" - {
        "주중 어느 날이든 그 주 금요일 00:00 ~ 월요일 00:00 을 가리킨다" {
            listOf(
                LocalDateTime.of(2026, 3, 9, 0, 0),
                LocalDateTime.of(2026, 3, 11, 9, 30),
                LocalDateTime.of(2026, 3, 15, 23, 59),
            ).forEach { at ->
                val period = ChatPeriod.weekendOf(at)

                period.opensAt shouldBe LocalDateTime.of(2026, 3, 13, 0, 0)
                period.expiresAt shouldBe LocalDateTime.of(2026, 3, 16, 0, 0)
            }
        }

        "주말이 이미 시작된 뒤에 만들어도 다음 주로 미루지 않는다" {
            val saturday = LocalDateTime.of(2026, 3, 14, 15, 0)

            val period = ChatPeriod.weekendOf(saturday)

            period.opensAt shouldBe LocalDateTime.of(2026, 3, 13, 0, 0)
            period.isOpenedAt(saturday) shouldBe true
        }

        "구간 길이는 72시간이다" {
            val period = ChatPeriod.weekendOf(LocalDateTime.of(2026, 3, 11, 9, 0))

            java.time.Duration.between(period.opensAt, period.expiresAt).toHours() shouldBe 72
        }
    }

    "isOpenedAt" - {
        "개방 시각 전이면 아직 열리지 않았다" {
            val period = ChatPeriod.weekendOf(LocalDateTime.of(2026, 3, 11, 9, 0))

            period.isOpenedAt(LocalDateTime.of(2026, 3, 12, 23, 59)) shouldBe false
            period.isOpenedAt(LocalDateTime.of(2026, 3, 13, 0, 0)) shouldBe true
        }
    }

    "of" - {
        "금요일이 아닌 날짜는 거부한다" {
            shouldThrow<IllegalArgumentException> {
                ChatPeriod.of(LocalDate.of(2026, 3, 12))
            }
        }
    }
})
