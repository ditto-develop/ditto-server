package com.ditto.domain.feedback.repository

import com.ditto.domain.feedback.entity.FeedbackImage
import org.springframework.data.jpa.repository.JpaRepository

interface FeedbackImageRepository : JpaRepository<FeedbackImage, Long> {

    fun findAllByFeedbackIdOrderByDisplayOrder(feedbackId: Long): List<FeedbackImage>
}
