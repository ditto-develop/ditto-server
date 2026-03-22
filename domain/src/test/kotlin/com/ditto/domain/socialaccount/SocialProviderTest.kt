package com.ditto.domain.socialaccount

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.domain.socialaccount.entity.SocialProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class SocialProviderTest : FreeSpec(
    {

        "from" - {
            "소문자로 변환할 수 있다" {
                SocialProvider.from("kakao") shouldBe SocialProvider.KAKAO
            }

            "대문자로 변환할 수 있다" {
                SocialProvider.from("KAKAO") shouldBe SocialProvider.KAKAO
            }

            "대소문자를 혼합해도 변환할 수 있다" {
                SocialProvider.from("Kakao") shouldBe SocialProvider.KAKAO
            }

            "지원하지 않는 제공자면 예외가 발생한다" {
                val exception = shouldThrow<ErrorException> {
                    SocialProvider.from("unknown")
                }
                exception.errorCode shouldBe ErrorCode.UNSUPPORTED_PROVIDER
            }
        }
    },
)
