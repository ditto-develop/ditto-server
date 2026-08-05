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

    "upcomingWeekendFrom" - {
        "주중이면 그 주 금요일을 가리킨다" {
            val period = ChatPeriod.upcomingWeekendFrom(LocalDateTime.of(2026, 3, 9, 10, 0))

            period.opensAt shouldBe LocalDateTime.of(2026, 3, 13, 0, 0)
            period.expiresAt shouldBe LocalDateTime.of(2026, 3, 16, 0, 0)
        }

        "금요일 00:00 정각이면 그 주말이다" {
            val opensAt = LocalDateTime.of(2026, 3, 13, 0, 0)

            ChatPeriod.upcomingWeekendFrom(opensAt) shouldBe ChatPeriod.of(LocalDate.of(2026, 3, 13))
            ChatPeriod.upcomingWeekendFrom(opensAt).opensAt shouldBe opensAt
        }

        // weekendOf 와 갈리는 지점 — 이미 시작된 주말에 합류시키지 않는다.
        "주말이 시작된 뒤라면 다음 금요일로 넘긴다" {
            listOf(
                LocalDateTime.of(2026, 3, 13, 0, 1),
                LocalDateTime.of(2026, 3, 14, 15, 0),
                LocalDateTime.of(2026, 3, 15, 23, 59, 40),
            ).forEach { at ->
                ChatPeriod.upcomingWeekendFrom(at).opensAt shouldBe LocalDateTime.of(2026, 3, 20, 0, 0)
            }
        }

        // 닫힌 구간으로 방을 만들면 ACTIVE 로 태어난 뒤 만료 스케줄러가 곧바로 끝낸다.
        // 이 팩토리의 존재 이유가 그 구간이 나오지 않게 하는 것이다.
        "돌려주는 구간은 언제나 기준 시각 이후에 열린다" {
            listOf(
                LocalDateTime.of(2026, 3, 9, 0, 0),
                LocalDateTime.of(2026, 3, 13, 0, 0),
                LocalDateTime.of(2026, 3, 13, 0, 1),
                LocalDateTime.of(2026, 3, 15, 23, 59, 59),
                LocalDateTime.of(2026, 3, 16, 0, 0),
            ).forEach { at ->
                val period = ChatPeriod.upcomingWeekendFrom(at)

                (period.opensAt >= at) shouldBe true
                period.isOpenedAt(at) shouldBe (period.opensAt == at)
            }
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

        "개방 시각이 종료 시각보다 늦으면 거부한다" {
            shouldThrow<IllegalArgumentException> {
                ChatPeriod(
                    opensAt = LocalDateTime.of(2026, 3, 16, 0, 0),
                    expiresAt = LocalDateTime.of(2026, 3, 13, 0, 0),
                )
            }
        }
    }
})
