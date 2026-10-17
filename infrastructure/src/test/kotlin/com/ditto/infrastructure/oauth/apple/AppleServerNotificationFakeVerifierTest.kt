package com.ditto.infrastructure.oauth.apple

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class AppleServerNotificationFakeVerifierTest : FreeSpec(
    {
        val verifier = AppleServerNotificationFakeVerifier()

        "이벤트타입:sub 형식을 읽는다" {
            val notification = verifier.verify("account-delete:001234.fake-apple-subject.0000")

            notification.type shouldBe AppleServerNotificationType.ACCOUNT_DELETED
            notification.subject shouldBe "001234.fake-apple-subject.0000"
        }

        "형식이 맞지 않으면 거부한다" {
            listOf("consent-revoked", "consent-revoked:", ":001234").forEach { payload ->
                val exception = shouldThrow<WarnException> { verifier.verify(payload) }
                exception.errorCode shouldBe ErrorCode.INVALID_APPLE_SERVER_NOTIFICATION
            }
        }
    },
)
