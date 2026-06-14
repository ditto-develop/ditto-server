package com.ditto.application.match.matching

import com.ditto.domain.member.entity.Gender
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

class MatchScoreCalculatorTest : FreeSpec(
    {
        // 점수 계산은 답변만 사용하므로 성별·나이는 임의값으로 둔다.
        fun participant(id: Long, answers: Map<Long, Long>) = MatchParticipant(id, answers, Gender.MALE, age = 25)

        "calculate" - {
            "모든 문항의 답이 같으면 100.0 점, matched=total" {
                val p1 = participant(1L, mapOf(101L to 1L, 102L to 1L, 103L to 1L))
                val p2 = participant(2L, mapOf(101L to 1L, 102L to 1L, 103L to 1L))

                val result = MatchScoreCalculator.calculate(p1, p2)

                result.score shouldBe (100.0 plusOrMinus 0.01)
                result.matchedQuestionCount shouldBe 3
                result.totalQuestionCount shouldBe 3
            }

            "일부만 같으면 (같은 답 수 ÷ 문항 수) × 100, 소수점 1자리" {
                val p1 = participant(1L, mapOf(101L to 1L, 102L to 1L, 103L to 1L))
                val p2 = participant(2L, mapOf(101L to 1L, 102L to 1L, 103L to 2L)) // 3문항 중 2개 일치

                val result = MatchScoreCalculator.calculate(p1, p2)

                result.score shouldBe (66.7 plusOrMinus 0.05) // 2/3 × 100
                result.matchedQuestionCount shouldBe 2
                result.totalQuestionCount shouldBe 3
            }

            "겹치는 답이 하나도 없으면 0.0 점, matched=0" {
                val p1 = participant(1L, mapOf(101L to 1L, 102L to 1L))
                val p2 = participant(2L, mapOf(101L to 2L, 102L to 2L))

                val result = MatchScoreCalculator.calculate(p1, p2)

                result.score shouldBe (0.0 plusOrMinus 0.01)
                result.matchedQuestionCount shouldBe 0
                result.totalQuestionCount shouldBe 2
            }

            "답변이 하나도 없으면 0.0 점 (문항 수 0 가드)" {
                val p1 = participant(1L, emptyMap())
                val p2 = participant(2L, emptyMap())

                val result = MatchScoreCalculator.calculate(p1, p2)

                result.score shouldBe (0.0 plusOrMinus 0.01)
                result.matchedQuestionCount shouldBe 0
                result.totalQuestionCount shouldBe 0
            }
        }
    },
)
