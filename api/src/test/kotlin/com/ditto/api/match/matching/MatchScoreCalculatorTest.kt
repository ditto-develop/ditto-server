package com.ditto.api.match.matching

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

class MatchScoreCalculatorTest : FreeSpec(
    {
        "calculate" - {
            "모든 문항의 답이 같으면 100.0 점" {
                val p1 = MatchParticipant(1L, mapOf(101L to 1L, 102L to 1L, 103L to 1L))
                val p2 = MatchParticipant(2L, mapOf(101L to 1L, 102L to 1L, 103L to 1L))

                MatchScoreCalculator.calculate(p1, p2) shouldBe (100.0 plusOrMinus 0.01)
            }

            "일부만 같으면 (같은 답 수 ÷ 문항 수) × 100, 소수점 1자리" {
                val p1 = MatchParticipant(1L, mapOf(101L to 1L, 102L to 1L, 103L to 1L))
                val p2 = MatchParticipant(2L, mapOf(101L to 1L, 102L to 1L, 103L to 2L)) // 3문항 중 2개 일치

                MatchScoreCalculator.calculate(p1, p2) shouldBe (66.7 plusOrMinus 0.05) // 2/3 × 100
            }

            "겹치는 답이 하나도 없으면 0.0 점" {
                val p1 = MatchParticipant(1L, mapOf(101L to 1L, 102L to 1L))
                val p2 = MatchParticipant(2L, mapOf(101L to 2L, 102L to 2L))

                MatchScoreCalculator.calculate(p1, p2) shouldBe (0.0 plusOrMinus 0.01)
            }

            "답변이 하나도 없으면 0.0 점 (문항 수 0 가드)" {
                val p1 = MatchParticipant(1L, emptyMap())
                val p2 = MatchParticipant(2L, emptyMap())

                MatchScoreCalculator.calculate(p1, p2) shouldBe (0.0 plusOrMinus 0.01)
            }
        }
    },
)
