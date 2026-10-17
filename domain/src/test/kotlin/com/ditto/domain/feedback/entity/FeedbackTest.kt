package com.ditto.domain.feedback.entity

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.feedback.FeedbackFixture
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

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

            "내용이 공백뿐이면 접수하지 않는다" {
                shouldThrow<WarnException> {
                    FeedbackFixture.create(content = "   ")
                }.errorCode shouldBe ErrorCode.BAD_REQUEST
            }
        }

        "complete" - {
            "처리 완료로 바꾸고 처리한 관리자와 시각을 남긴다" {
                val feedback = FeedbackFixture.create()
                val now = LocalDateTime.of(2026, 10, 17, 15, 0)

                feedback.complete(adminName = "운영자", now = now)

                feedback.status shouldBe FeedbackStatus.COMPLETED
                feedback.completedByName shouldBe "운영자"
                feedback.completedAt shouldBe now
            }

            "이미 처리 완료한 의견은 다시 처리하지 않는다" {
                val feedback = FeedbackFixture.create()
                feedback.complete(adminName = "운영자", now = LocalDateTime.of(2026, 10, 17, 15, 0))

                shouldThrow<WarnException> {
                    feedback.complete(adminName = "다른 운영자", now = LocalDateTime.of(2026, 10, 17, 16, 0))
                }.errorCode shouldBe ErrorCode.BAD_REQUEST
                feedback.completedByName shouldBe "운영자"
            }
        }
    },
)
