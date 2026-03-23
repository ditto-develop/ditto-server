package com.ditto.infrastructure.oauth

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.domain.socialaccount.entity.SocialProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk

class OAuthClientFactoryTest : FreeSpec({

    "getClient" - {
        "등록된 provider의 클라이언트를 반환한다" {
            val kakaoClient = mockk<OAuthClient>()
            val factory = OAuthClientFactory(mapOf(SocialProvider.KAKAO to kakaoClient))

            factory.getClient(SocialProvider.KAKAO) shouldBe kakaoClient
        }

        "등록되지 않은 provider면 예외가 발생한다" {
            val factory = OAuthClientFactory(emptyMap())

            val exception = shouldThrow<ErrorException> {
                factory.getClient(SocialProvider.KAKAO)
            }
            exception.errorCode shouldBe ErrorCode.UNSUPPORTED_PROVIDER
        }
    }
})
