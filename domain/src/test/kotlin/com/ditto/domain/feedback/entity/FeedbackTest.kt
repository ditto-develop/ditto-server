package com.ditto.domain.feedback.entity

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.feedback.FeedbackFixture
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class FeedbackTest : FreeSpec(
    {
        "receive" - {
            "접수하면 기기 정보와 함께 확인 대기 상태로 생성된다" {
                val feedback = FeedbackFixture.create(
                    memberId = 3L,
                    type = FeedbackType.SUGGESTION,
                    content = "다크 모드가 있으면 좋겠어요.",
                    deviceInfo = FeedbackDeviceInfo(appVersion = "1.4.0", os = "iOS 18.2", deviceModel = "iPhone15,2"),
                )

                feedback.memberId shouldBe 3L
                feedback.type shouldBe FeedbackType.SUGGESTION
                feedback.content shouldBe "다크 모드가 있으면 좋겠어요."
                feedback.appVersion shouldBe "1.4.0"
                feedback.os shouldBe "iOS 18.2"
                feedback.deviceModel shouldBe "iPhone15,2"
                feedback.status shouldBe FeedbackStatus.RECEIVED
            }

            "내용이 공백뿐이면 거부한다" {
                shouldThrow<WarnException> {
                    FeedbackFixture.create(content = "   ")
                }.errorCode shouldBe ErrorCode.BAD_REQUEST
            }
        }
    },
)
