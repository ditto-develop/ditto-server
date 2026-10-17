package com.ditto.domain.feedback.entity

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class FeedbackImageTest : FreeSpec(
    {
        "attachAll" - {
            "첨부 순서대로 이미지를 만든다" {
                val images = FeedbackImage.attachAll(
                    feedbackId = 1L,
                    objectKeys = listOf("feedbacks/1/a", "feedbacks/1/b"),
                )

                images.map { it.objectKey } shouldBe listOf("feedbacks/1/a", "feedbacks/1/b")
                images.map { it.displayOrder } shouldBe listOf(0, 1)
                images.map { it.feedbackId } shouldBe listOf(1L, 1L)
            }

            "3장을 넘으면 접수하지 않는다" {
                shouldThrow<WarnException> {
                    FeedbackImage.attachAll(feedbackId = 1L, objectKeys = List(4) { "feedbacks/1/$it" })
                }.errorCode shouldBe ErrorCode.ATTACHMENT_IMAGE_LIMIT_EXCEEDED
            }

            "같은 키를 두 번 붙이면 접수하지 않는다" {
                shouldThrow<WarnException> {
                    FeedbackImage.attachAll(feedbackId = 1L, objectKeys = listOf("feedbacks/1/a", "feedbacks/1/a"))
                }.errorCode shouldBe ErrorCode.INVALID_ATTACHMENT_IMAGE_KEY
            }
        }
    },
)
