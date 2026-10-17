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

        "없는 code면 거부한다" {
            shouldThrow<WarnException> {
                FeedbackType.from("question")
            }.errorCode shouldBe ErrorCode.BAD_REQUEST
        }
    },
)
