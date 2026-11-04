package com.ditto.api.chat.quiz

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class GroupAnswerSpreadTest : FreeSpec({

    "답한 사람이 모두 한 칸에 있으면 만장일치이고 그 선택지를 안다" {
        val spread = GroupAnswerSpread(mapOf(11L to 3, 12L to 0))

        spread.unanimous shouldBe true
        spread.unanimousChoiceId shouldBe 11L
    }

    "아무도 답하지 않았거나 갈렸으면 만장일치가 아니다" {
        GroupAnswerSpread(mapOf(11L to 0, 12L to 0)).unanimousChoiceId.shouldBeNull()
        GroupAnswerSpread(mapOf(11L to 2, 12L to 1)).unanimous shouldBe false
    }

    "인원 차는 가장 많은 칸과 가장 적은 칸의 차이다" {
        GroupAnswerSpread(mapOf(11L to 2, 12L to 2)).gap shouldBe 0
        GroupAnswerSpread(mapOf(11L to 3, 12L to 1)).let {
            it.gap shouldBe 2
            it.countOf(12L) shouldBe 1
            it.countOf(99L) shouldBe 0
        }
    }
})
