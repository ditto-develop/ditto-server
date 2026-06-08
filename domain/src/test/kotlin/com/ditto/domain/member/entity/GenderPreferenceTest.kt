package com.ditto.domain.member.entity

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class GenderPreferenceTest : FreeSpec(
    {
        "targetGenders" - {
            "OPPOSITE 는 owner 의 반대 성별만 대상으로 한다" {
                GenderPreference.OPPOSITE.targetGenders(Gender.MALE) shouldBe setOf(Gender.FEMALE)
                GenderPreference.OPPOSITE.targetGenders(Gender.FEMALE) shouldBe setOf(Gender.MALE)
            }

            "SAME 은 owner 와 같은 성별만 대상으로 한다" {
                GenderPreference.SAME.targetGenders(Gender.MALE) shouldBe setOf(Gender.MALE)
                GenderPreference.SAME.targetGenders(Gender.FEMALE) shouldBe setOf(Gender.FEMALE)
            }

            "ANY 는 모든 성별을 대상으로 한다" {
                GenderPreference.ANY.targetGenders(Gender.MALE) shouldBe setOf(Gender.MALE, Gender.FEMALE)
                GenderPreference.ANY.targetGenders(Gender.FEMALE) shouldBe setOf(Gender.MALE, Gender.FEMALE)
            }
        }
    },
)
