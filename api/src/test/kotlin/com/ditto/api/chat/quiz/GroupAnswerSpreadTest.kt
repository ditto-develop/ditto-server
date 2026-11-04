package com.ditto.api.chat.quiz

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class GroupAnswerSpreadTest : FreeSpec({

    "답한 사람이 모두 한 칸에 있으면 만장일치다" {
        GroupAnswerSpread(listOf(3, 0)).unanimous shouldBe true
    }

    "아무도 답하지 않았으면 만장일치가 아니다" {
        GroupAnswerSpread(listOf(0, 0)).unanimous shouldBe false
    }

    "인원 차는 가장 많은 칸과 가장 적은 칸의 차이다" {
        GroupAnswerSpread(listOf(2, 2)).gap shouldBe 0
        GroupAnswerSpread(listOf(3, 1)).gap shouldBe 2
    }
})
