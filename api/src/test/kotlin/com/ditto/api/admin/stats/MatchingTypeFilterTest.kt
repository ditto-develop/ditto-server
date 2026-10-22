package com.ditto.api.admin.stats

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class MatchingTypeFilterTest : FreeSpec(
    {
        "quizMatchingTypeLabels" - {
            "전체면 두 유형을, 하나를 고르면 그 유형만 내린다" {
                MatchingTypeFilter.ALL.quizMatchingTypeLabels() shouldBe mapOf("ONE_TO_ONE" to "1:1", "GROUP" to "그룹")
                MatchingTypeFilter.GROUP.quizMatchingTypeLabels() shouldBe mapOf("GROUP" to "그룹")
            }
        }

        "memberReviewMatchTypeLabels" - {
            "1:1 은 평가의 PERSONAL 채팅방 유형으로 내린다" {
                MatchingTypeFilter.ONE_TO_ONE.memberReviewMatchTypeLabels() shouldBe mapOf("PERSONAL" to "1:1")
            }
        }
    },
)
