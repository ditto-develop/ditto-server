package com.ditto.api.feedback

import com.ditto.api.feedback.dto.CreateFeedbackRequest
import com.ditto.api.feedback.dto.IssueFeedbackImageUploadUrlsRequest
import com.ditto.api.feedback.service.FeedbackService
import com.ditto.api.support.IntegrationTest
import com.ditto.api.support.image.ImageUploadFileRequest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.feedback.entity.FeedbackStatus
import com.ditto.domain.feedback.entity.FeedbackType
import com.ditto.domain.feedback.repository.FeedbackImageRepository
import com.ditto.domain.feedback.repository.FeedbackRepository
import com.ditto.infrastructure.storage.ObjectStorage
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import javax.sql.DataSource
import org.springframework.data.repository.findByIdOrNull

class FeedbackServiceTest(
    private val feedbackService: FeedbackService,
    private val feedbackRepository: FeedbackRepository,
    private val feedbackImageRepository: FeedbackImageRepository,
    private val objectStorage: ObjectStorage,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun issueUploadedKeys(memberId: Long, count: Int): List<String> {
        val request = IssueFeedbackImageUploadUrlsRequest(
            files = List(count) { ImageUploadFileRequest(contentType = "image/png", contentLength = 1024L) },
        )
        return feedbackService.issueImageUploadUrls(memberId, request).uploads.map { it.objectKey }
    }

    fun submitWithImages(memberId: Long, imageKeys: List<String>) = feedbackService.submitFeedback(
        memberId = memberId,
        request = CreateFeedbackRequest(type = "bug", content = "사진이 안 보여요.", imageKeys = imageKeys),
    )

    "의견 이미지 업로드 URL 발급" - {

        "회원별 의견 pending 경로로 객체 키를 발급한다" {
            val keys = issueUploadedKeys(memberId = 7L, count = 2)

            keys.size shouldBe 2
            keys.forEach { it shouldStartWith "pending/feedbacks/7/" }
        }
    }

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

        "이미지를 첨부하면 확정 경로로 옮기고 순서대로 저장한다" {
            val pendingKeys = issueUploadedKeys(memberId = 7L, count = 2)

            val result = submitWithImages(memberId = 7L, imageKeys = pendingKeys)

            val images = feedbackImageRepository.findAllByFeedbackIdOrderByDisplayOrder(result.id)
            val permanentKeys = pendingKeys.map { it.removePrefix("pending/") }
            images.map { it.objectKey } shouldBe permanentKeys
            permanentKeys.forEach { objectStorage.exists(it) shouldBe true }
            pendingKeys.forEach { objectStorage.exists(it) shouldBe false }
        }

        "발급받지 않은 이미지 키는 거부한다" {
            shouldThrow<WarnException> {
                submitWithImages(memberId = 7L, imageKeys = listOf("pending/feedbacks/7/forged-key"))
            }.errorCode shouldBe ErrorCode.INVALID_ATTACHMENT_IMAGE_KEY
        }

        "다른 회원이 발급받은 이미지 키는 거부한다" {
            val otherMemberKeys = issueUploadedKeys(memberId = 8L, count = 1)

            shouldThrow<WarnException> {
                submitWithImages(memberId = 7L, imageKeys = otherMemberKeys)
            }.errorCode shouldBe ErrorCode.INVALID_ATTACHMENT_IMAGE_KEY
        }

        "신고용으로 발급받은 이미지 키는 거부한다" {
            val userReportKey = "pending/user-reports/7/report-image"
            // 테스트용 가짜 스토리지는 URL 발급만으로 업로드를 마친 것으로 본다.
            objectStorage.issueUploadUrl(userReportKey, "image/png", 1024L)

            shouldThrow<WarnException> {
                submitWithImages(memberId = 7L, imageKeys = listOf(userReportKey))
            }.errorCode shouldBe ErrorCode.INVALID_ATTACHMENT_IMAGE_KEY
        }
    }
})
