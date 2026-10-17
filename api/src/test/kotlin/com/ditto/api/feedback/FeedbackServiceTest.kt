package com.ditto.api.feedback

import com.ditto.api.feedback.dto.CreateFeedbackRequest
import com.ditto.api.feedback.service.FeedbackService
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.feedback.entity.FeedbackStatus
import com.ditto.domain.feedback.entity.FeedbackType
import com.ditto.domain.feedback.repository.FeedbackRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import javax.sql.DataSource
import org.springframework.data.repository.findByIdOrNull

class FeedbackServiceTest(
    private val feedbackService: FeedbackService,
    private val feedbackRepository: FeedbackRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "의견 접수" - {

        "유형 code와 기기 정보를 담아 확인 대기 상태로 저장한다" {
            val request = CreateFeedbackRequest(
                type = "bug",
                content = "채팅방에서 사진이 안 보여요.",
                appVersion = "1.4.0",
                os = "Android 15",
                deviceModel = "SM-S928N",
            )

            val result = feedbackService.submitFeedback(memberId = 7L, request = request)

            val saved = feedbackRepository.findByIdOrNull(result.id) ?: error("의견이 저장되지 않았다")
            saved.memberId shouldBe 7L
            saved.type shouldBe FeedbackType.BUG
            saved.content shouldBe "채팅방에서 사진이 안 보여요."
            saved.appVersion shouldBe "1.4.0"
            saved.os shouldBe "Android 15"
            saved.deviceModel shouldBe "SM-S928N"
            saved.status shouldBe FeedbackStatus.RECEIVED
        }

        "없는 유형 code면 거부하고 저장하지 않는다" {
            val request = CreateFeedbackRequest(type = "question", content = "답변 언제 오나요?")

            shouldThrow<WarnException> {
                feedbackService.submitFeedback(memberId = 7L, request = request)
            }.errorCode shouldBe ErrorCode.BAD_REQUEST
            feedbackRepository.count() shouldBe 0L
        }
    }
})
