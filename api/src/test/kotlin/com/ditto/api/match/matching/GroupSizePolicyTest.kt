package com.ditto.api.match.matching

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class GroupSizePolicyTest : FreeSpec(
    {
        "정원 결정" - {
            "풀이 10명 미만이면 풀 크기와 6명 중 작은 쪽이다" {
                GroupSizePolicy.decide(3) shouldBe 3
                GroupSizePolicy.decide(5) shouldBe 5
                GroupSizePolicy.decide(6) shouldBe 6
                GroupSizePolicy.decide(9) shouldBe 6
            }

            "풀이 10명 이상 30명 미만이면 5명이다" {
                GroupSizePolicy.decide(10) shouldBe 5
                GroupSizePolicy.decide(29) shouldBe 5
            }

            "풀이 30명 이상이면 4명이다" {
                GroupSizePolicy.decide(30) shouldBe 4
                GroupSizePolicy.decide(205) shouldBe 4
            }

            "정원은 성사 최소 인원보다 작아지지 않는다" {
                GroupSizePolicy.decide(GroupSizePolicy.MIN_SIZE) shouldBe GroupSizePolicy.MIN_SIZE
            }
        }
    },
)
