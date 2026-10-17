package com.ditto.domain.feedback.entity

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class FeedbackTypeTest : FreeSpec(
    {
        "code로 유형을 찾는다" {
            FeedbackType.from("bug") shouldBe FeedbackType.BUG
            FeedbackType.from("suggestion") shouldBe FeedbackType.SUGGESTION
        }

        "없는 code면 들어온 값을 메시지에 담아 거부한다" {
            val exception = shouldThrow<WarnException> {
                FeedbackType.from("BUG")
            }

            exception.errorCode shouldBe ErrorCode.BAD_REQUEST
            exception.message shouldBe "지원하지 않는 의견 유형입니다: BUG"
        }
    },
)
