package com.ditto.domain.feedback.repository

import com.ditto.domain.feedback.entity.Feedback
import com.ditto.domain.feedback.entity.FeedbackStatus
import com.ditto.domain.feedback.entity.FeedbackType
import org.springframework.data.jpa.repository.JpaRepository

interface FeedbackRepository : JpaRepository<Feedback, Long> {

    fun findAllByStatusOrderByIdDesc(status: FeedbackStatus): List<Feedback>

    fun findAllByStatusAndTypeOrderByIdDesc(status: FeedbackStatus, type: FeedbackType): List<Feedback>
}
