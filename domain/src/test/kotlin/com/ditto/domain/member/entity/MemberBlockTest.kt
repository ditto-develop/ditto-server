package com.ditto.domain.member.entity

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class MemberBlockTest : FreeSpec(
    {
        "create" - {
            "차단한 사람과 차단된 사람을 방향 그대로 담는다" {
                val block = MemberBlock.create(blockerId = 1L, blockedMemberId = 2L)

                block.blockerId shouldBe 1L
                block.blockedMemberId shouldBe 2L
            }

            "반대 방향은 별개 행이다 — 서로 차단하면 두 행이 된다" {
                val forward = MemberBlock.create(blockerId = 1L, blockedMemberId = 2L)
                val backward = MemberBlock.create(blockerId = 2L, blockedMemberId = 1L)

                forward.blockerId shouldBe backward.blockedMemberId
                forward.blockedMemberId shouldBe backward.blockerId
            }
        }
    },
)
