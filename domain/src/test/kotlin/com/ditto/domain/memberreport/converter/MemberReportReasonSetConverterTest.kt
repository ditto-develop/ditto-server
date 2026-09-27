package com.ditto.domain.memberreport.converter

import com.ditto.domain.memberreport.entity.MemberReportReason
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class MemberReportReasonSetConverterTest : FreeSpec(
    {
        val converter = MemberReportReasonSetConverter()

        "선언 순서로 직렬화해 같은 조합은 같은 값이 된다" {
            converter.convertToDatabaseColumn(setOf(MemberReportReason.ETC, MemberReportReason.MONEY_DEMAND)) shouldBe
                "MONEY_DEMAND,ETC"
        }

        "기존 단일 사유 행(백필 값)을 집합으로 읽는다" {
            converter.convertToEntityAttribute("MONEY_DEMAND") shouldBe setOf(MemberReportReason.MONEY_DEMAND)
            converter.convertToEntityAttribute("UNDERAGE,INAPPROPRIATE_BEHAVIOR").toList() shouldBe
                listOf(MemberReportReason.INAPPROPRIATE_BEHAVIOR, MemberReportReason.UNDERAGE)
        }

        "빈 값은 빈 집합이다" {
            converter.convertToEntityAttribute("").shouldBeEmpty()
            converter.convertToEntityAttribute(null).shouldBeEmpty()
            converter.convertToDatabaseColumn(null) shouldBe ""
        }
    },
)
