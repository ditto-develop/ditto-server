package com.ditto.api.feedback.service

import com.ditto.api.feedback.dto.CreateFeedbackRequest
import com.ditto.api.feedback.dto.CreateFeedbackResponse
import com.ditto.domain.feedback.entity.Feedback
import com.ditto.domain.feedback.entity.FeedbackType
import com.ditto.domain.feedback.repository.FeedbackRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FeedbackService(
    private val feedbackRepository: FeedbackRepository,
) {

    @Transactional
    fun submitFeedback(memberId: Long, request: CreateFeedbackRequest): CreateFeedbackResponse {
        val feedback = Feedback.receive(
            memberId = memberId,
            type = FeedbackType.from(request.type),
            content = request.content,
            deviceInfo = request.toDeviceInfo(),
        )
        val saved = feedbackRepository.save(feedback)
        return CreateFeedbackResponse(id = saved.id)
    }
}
