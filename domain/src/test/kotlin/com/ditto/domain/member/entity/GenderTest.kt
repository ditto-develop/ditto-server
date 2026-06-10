package com.ditto.domain.member.entity

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class GenderTest : FreeSpec(
    {
        "opposite() 는 반대 성별을 반환한다" {
            Gender.MALE.opposite() shouldBe Gender.FEMALE
            Gender.FEMALE.opposite() shouldBe Gender.MALE
        }
    },
)
