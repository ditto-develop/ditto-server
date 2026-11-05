package com.ditto.api.quiz.stat

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class QuizStatCalculatorTest : FreeSpec({

    "비율은 문항 안 합이 100이 되도록 최대 잉여법으로 보정한다" - {
        "1:1:1 은 34·33·33 — 남는 1은 앞 선택지가 받는다" {
            QuizStatCalculator.percentsSummingTo100(listOf(1, 1, 1)) shouldBe listOf(34, 33, 33)
        }

        "2:1 은 67·33 — 소수부가 큰 쪽이 올라간다" {
            QuizStatCalculator.percentsSummingTo100(listOf(2, 1)) shouldBe listOf(67, 33)
        }

        "아무도 고르지 않은 문항은 모두 0이다" {
            QuizStatCalculator.percentsSummingTo100(listOf(0, 0)) shouldBe listOf(0, 0)
        }
    }

    "소수 의견은 선택 수 × 5 ≤ 참여자 수일 때다" - {
        "참여자 20명 중 4명(20%)은 소수 의견이다" {
            QuizStatCalculator.isMinority(selectCount = 4, participantCount = 20) shouldBe true
        }

        "참여자 21명 중 5명(23.8%)은 소수 의견이 아니다 — 반올림한 %가 아니라 실제 수로 판정한다" {
            QuizStatCalculator.isMinority(selectCount = 5, participantCount = 21) shouldBe false
        }

        "참여자가 5명이면 1명이 고른 답도 소수 의견이다" {
            QuizStatCalculator.isMinority(selectCount = 1, participantCount = 5) shouldBe true
        }
    }

    "겹친 사람 기준은 문항 수 × 2/3 올림이다" {
        QuizStatCalculator.overlapThreshold(12) shouldBe 8
        QuizStatCalculator.overlapThreshold(10) shouldBe 7
        QuizStatCalculator.overlapThreshold(15) shouldBe 10
    }

    "완주자의 답으로 선택지 집계·소수 의견 수·겹친 사람 수를 낸다" {
        // 문항 3개 → 겹친 사람 기준 2개. 참여자 20명: 회원 1은 문항 1에서 혼자 2번(5%)을 골랐다.
        val choices = mapOf(10L to listOf(101L, 102L), 20L to listOf(201L, 202L), 30L to listOf(301L, 302L))
        val answers = (1L..20L).associateWith { memberId ->
            mapOf(
                10L to if (memberId == 1L) 102L else 101L,
                20L to if (memberId <= 10L) 201L else 202L,
                30L to 301L,
            )
        }

        val result = QuizStatCalculator.calculate(choices, answers)

        result.participantCount shouldBe 20
        result.overlapThreshold shouldBe 2
        result.choices.first { it.choiceId == 102L }.let {
            it.selectCount shouldBe 1
            it.percent shouldBe 5
            it.minority shouldBe true
        }
        result.choices.first { it.choiceId == 302L }.percent shouldBe 0
        val member1 = result.members.first { it.memberId == 1L }
        member1.minorityCount shouldBe 1
        // 회원 1과 2개 이상 같은 사람: 문항 2·3이 같은 2~10번 9명 + 문항 3만 같은 11~20번은 1개뿐이라 제외.
        member1.overlapCount shouldBe 9
        // 회원 2: 1번(문항 2·3 같음) + 3~10번 8명(셋 다 같음) + 11~20번(문항 1·3 같음) 10명 = 19명, 본인 제외.
        result.members.first { it.memberId == 2L }.overlapCount shouldBe 19
    }

    "완주자가 없으면 참여자 0명·비율 0으로 낸다" {
        val result = QuizStatCalculator.calculate(mapOf(10L to listOf(101L, 102L)), emptyMap())

        result.participantCount shouldBe 0
        result.choices.map { it.percent } shouldBe listOf(0, 0)
        result.members shouldBe emptyList()
    }
})
