package com.ditto.domain.member.entity

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class MemberNotificationSettingTest : FreeSpec(
    {
        "defaultOf" - {
            "매칭·채팅은 수신, 마케팅은 미수신이 기본이다" {
                val setting = MemberNotificationSetting.defaultOf(memberId = 1L)

                setting.memberId shouldBe 1L
                setting.matching shouldBe true
                setting.chat shouldBe true
                setting.marketing shouldBe false
            }
        }

        "update" - {
            "값이 온 항목만 바꾼다" {
                val setting = MemberNotificationSetting.defaultOf(memberId = 1L)

                setting.update(matching = null, chat = false, marketing = null)

                setting.matching shouldBe true
                setting.chat shouldBe false
                setting.marketing shouldBe false
            }

            "세 항목을 한 번에 바꿀 수 있다" {
                val setting = MemberNotificationSetting.defaultOf(memberId = 1L)

                setting.update(matching = false, chat = false, marketing = true)

                setting.matching shouldBe false
                setting.chat shouldBe false
                setting.marketing shouldBe true
            }

            "모두 null이면 아무것도 바뀌지 않는다" {
                val setting = MemberNotificationSetting.defaultOf(memberId = 1L)

                setting.update(matching = null, chat = null, marketing = null)

                setting.matching shouldBe true
                setting.chat shouldBe true
                setting.marketing shouldBe false
            }
        }
    },
)
