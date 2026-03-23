package com.ditto.domain.socialaccount.entity

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class SocialProviderTest : FreeSpec(
    {

        "from" - {
            "소문자 provider 이름으로 변환한다" {
                SocialProvider.from("kakao") shouldBe SocialProvider.KAKAO
            }

            "대문자 provider 이름으로 변환한다" {
                SocialProvider.from("KAKAO") shouldBe SocialProvider.KAKAO
            }

            "대소문자 혼합 provider 이름으로 변환한다" {
                SocialProvider.from("Kakao") shouldBe SocialProvider.KAKAO
            }

            "지원하지 않는 provider면 ErrorException을 던진다" {
                val exception = shouldThrow<ErrorException> {
                    SocialProvider.from("google")
                }
                exception.errorCode shouldBe ErrorCode.UNSUPPORTED_PROVIDER
            }
        }
    },
)
