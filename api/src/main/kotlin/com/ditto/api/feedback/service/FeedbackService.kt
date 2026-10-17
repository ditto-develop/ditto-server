package com.ditto.api.feedback.service

import com.ditto.api.feedback.dto.CreateFeedbackRequest
import com.ditto.api.feedback.dto.CreateFeedbackResponse
import com.ditto.api.feedback.dto.IssueFeedbackImageUploadUrlsRequest
import com.ditto.api.support.image.ImageUploadCategory
import com.ditto.api.support.image.ImageUploadUrlsResponse
import com.ditto.api.support.image.PendingImageStorage
import com.ditto.domain.feedback.entity.Feedback
import com.ditto.domain.feedback.entity.FeedbackImage
import com.ditto.domain.feedback.entity.FeedbackType
import com.ditto.domain.feedback.repository.FeedbackImageRepository
import com.ditto.domain.feedback.repository.FeedbackRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FeedbackService(
    private val feedbackRepository: FeedbackRepository,
    private val feedbackImageRepository: FeedbackImageRepository,
    private val pendingImageStorage: PendingImageStorage,
) {

    fun issueImageUploadUrls(memberId: Long, request: IssueFeedbackImageUploadUrlsRequest): ImageUploadUrlsResponse =
        pendingImageStorage.issueUploadUrls(ImageUploadCategory.FEEDBACK, memberId, request.files)

    @Transactional
    fun submitFeedback(memberId: Long, request: CreateFeedbackRequest): CreateFeedbackResponse {
        val feedback = Feedback.receive(
            memberId = memberId,
            type = FeedbackType.from(request.type),
            content = request.content,
            deviceInfo = request.toDeviceInfo(),
        )
        val saved = feedbackRepository.save(feedback)
        val images = FeedbackImage.attachAll(
            feedbackId = saved.id,
            objectKeys = pendingImageStorage.toPermanentKeys(request.imageKeys),
        )
        pendingImageStorage.validateUploadedBy(ImageUploadCategory.FEEDBACK, memberId, request.imageKeys)
        feedbackImageRepository.saveAll(images)
        pendingImageStorage.moveToPermanent(request.imageKeys)
        return CreateFeedbackResponse(id = saved.id)
    }
}
