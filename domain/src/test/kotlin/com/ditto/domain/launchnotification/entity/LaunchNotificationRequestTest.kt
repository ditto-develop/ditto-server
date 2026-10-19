package com.ditto.domain.launchnotification.entity

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.launchnotification.LaunchNotificationRequestFixture
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

class LaunchNotificationRequestTest : FreeSpec(
    {
        "request" - {
            "이메일은 앞뒤 공백을 지우고 소문자로 저장한다" {
                val request = LaunchNotificationRequestFixture.create(email = "  Visitor@Example.COM ")

                request.email shouldBe "visitor@example.com"
                request.notifiedAt shouldBe null
            }

            "골뱅이나 도메인 점이 없거나 공백이 섞인 이메일은 거부한다" {
                listOf("visitor", "visitor@example", "vis itor@example.com", "@example.com", "").forEach { email ->
                    shouldThrow<WarnException> {
                        LaunchNotificationRequestFixture.create(email = email)
                    }.errorCode shouldBe ErrorCode.BAD_REQUEST
                }
            }

            "254자를 넘는 이메일은 거부한다" {
                val local = "a".repeat(64)
                val tooLong = "$local@" + "b".repeat(254 - local.length - 1 - 4 + 1) + ".com"

                shouldThrow<WarnException> {
                    LaunchNotificationRequestFixture.create(email = tooLong)
                }.errorCode shouldBe ErrorCode.BAD_REQUEST
            }

            "동의 문구 버전이 비었거나 30자를 넘으면 거부한다" {
                listOf(" ", "v".repeat(31)).forEach { version ->
                    shouldThrow<WarnException> {
                        LaunchNotificationRequestFixture.create(consentVersion = version)
                    }.errorCode shouldBe ErrorCode.BAD_REQUEST
                }
            }
        }

        "reconsent" - {
            "다시 동의하면 동의 시각과 버전만 바뀐다" {
                val request = LaunchNotificationRequestFixture.create(consentVersion = "2026-10-19")
                val later = LocalDateTime.of(2026, 11, 1, 9, 0)

                request.reconsent("2026-11-01", later)

                request.consentVersion shouldBe "2026-11-01"
                request.consentedAt shouldBe later
                request.email shouldBe "visitor@example.com"
            }
        }

        "LaunchPlatform.from" - {
            "모르는 플랫폼은 거부한다" {
                shouldThrow<WarnException> { LaunchPlatform.from("IOS") }.errorCode shouldBe ErrorCode.BAD_REQUEST
            }
        }
    },
)
