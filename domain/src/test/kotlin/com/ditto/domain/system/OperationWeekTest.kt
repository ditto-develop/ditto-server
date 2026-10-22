package com.ditto.domain.system

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime

class OperationWeekTest : FreeSpec(
    {
        "containing" - {
            "월 경계에 걸친 월요일~일요일(2026-07-27~08-02)은 모두 같은 주로 스냅된다" {
                val monday = LocalDate.of(2026, 7, 27)
                val weekDates = (0L..6L).map { monday.plusDays(it) }

                weekDates.forEach { date ->
                    OperationWeek.containing(date).startedOn shouldBe monday
                }
            }

            "연 경계에 걸친 월요일~일요일(2026-12-28~2027-01-03)은 모두 같은 주로 스냅된다" {
                val monday = LocalDate.of(2026, 12, 28)
                val weekDates = (0L..6L).map { monday.plusDays(it) }

                weekDates.forEach { date ->
                    OperationWeek.containing(date).startedOn shouldBe monday
                }
            }

            "월요일 날짜는 자기 자신이 시작일이 된다" {
                val monday = LocalDate.of(2026, 8, 3)

                OperationWeek.containing(monday).startedOn shouldBe monday
            }

            "다른 주의 날짜는 다른 시작일을 가진다" {
                val sunday = LocalDate.of(2026, 8, 2)
                val nextMonday = LocalDate.of(2026, 8, 3)

                OperationWeek.containing(sunday).startedOn shouldBe LocalDate.of(2026, 7, 27)
                OperationWeek.containing(nextMonday).startedOn shouldBe nextMonday
            }
        }

        "생성 검증" - {
            "월요일이 아닌 날짜로 직접 생성하면 예외가 발생한다" {
                shouldThrow<IllegalArgumentException> {
                    OperationWeek(LocalDate.of(2026, 7, 28))
                }
            }
        }

        "표시용 파생값" - {
            "월 경계 주의 표시값은 시작 월요일 기준으로 계산된다 (8월 1일 → 2026년 7월 5주차)" {
                val week = OperationWeek.containing(LocalDate.of(2026, 8, 1))

                week.year shouldBe 2026
                week.month shouldBe 7
                week.weekOfMonth shouldBe 5
            }

            "연 경계 주의 표시값은 시작 월요일 기준으로 계산된다 (2027년 1월 1일 → 2026년 12월 5주차)" {
                val week = OperationWeek.containing(LocalDate.of(2027, 1, 1))

                week.year shouldBe 2026
                week.month shouldBe 12
                week.weekOfMonth shouldBe 5
            }
        }

        "dateTimeRange" - {
            "월요일 00:00 에 시작해 다음 주 월요일 00:00 직전까지다" {
                val week = OperationWeek(LocalDate.of(2026, 10, 19))

                week.dateTimeRange.start shouldBe LocalDateTime.of(2026, 10, 19, 0, 0)
                week.dateTimeRange.endExclusive shouldBe LocalDateTime.of(2026, 10, 26, 0, 0)
            }
        }

        "minusWeeks" - {
            "연 경계를 넘어 앞 주로 간다" {
                OperationWeek(LocalDate.of(2027, 1, 4)).minusWeeks(2).startedOn shouldBe LocalDate.of(2026, 12, 21)
            }
        }
    },
)
