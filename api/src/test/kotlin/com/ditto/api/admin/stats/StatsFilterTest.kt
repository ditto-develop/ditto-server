package com.ditto.api.admin.stats

import com.ditto.api.admin.stats.dto.StatsFilter
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.system.OperationWeek
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime

private val CURRENT_WEEK = OperationWeek(LocalDate.of(2026, 10, 19))

class StatsFilterTest : FreeSpec(
    {
        "selectedWeek" - {
            "회차를 고르지 않으면 이번 회차다" {
                StatsFilter().selectedWeek(CURRENT_WEEK) shouldBe CURRENT_WEEK
            }

            "고른 날이 속한 주의 월요일로 맞춘다" {
                StatsFilter(week = LocalDate.of(2026, 10, 15)).selectedWeek(CURRENT_WEEK).startedOn shouldBe
                    LocalDate.of(2026, 10, 12)
            }
        }

        "hasConditions" - {
            "회차만 고르면 조건이 없다" {
                StatsFilter(week = LocalDate.of(2026, 10, 15)).hasConditions() shouldBe false
            }

            "매칭 유형이나 회원 조건을 하나라도 걸면 조건이 있다" {
                StatsFilter(matchingType = MatchingTypeFilter.GROUP).hasConditions() shouldBe true
                StatsFilter(joinedWeek = LocalDate.of(2026, 10, 1)).hasConditions() shouldBe true
            }
        }

        "toMemberFilter" - {
            "성별·연령대·가입 주차를 회원 조회 조건으로 바꾼다" {
                val filter = StatsFilter(
                    gender = Gender.FEMALE,
                    ageBand = AgeBand.THIRTIES,
                    joinedWeek = LocalDate.of(2026, 10, 1),
                )
                val today = LocalDate.of(2026, 10, 22)

                val memberFilter = filter.toMemberFilter(today)

                memberFilter.gender shouldBe Gender.FEMALE
                memberFilter.birthDateRange shouldBe AgeBand.THIRTIES.birthDateRange(today)
                memberFilter.joinedAtRange shouldBe
                    LocalDateTime.of(2026, 9, 28, 0, 0)..<LocalDateTime.of(2026, 10, 5, 0, 0)
            }
        }
    },
)
