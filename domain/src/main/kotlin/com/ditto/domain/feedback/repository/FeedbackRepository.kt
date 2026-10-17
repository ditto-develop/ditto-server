package com.ditto.domain.feedback.repository

import com.ditto.domain.feedback.entity.Feedback
import org.springframework.data.jpa.repository.JpaRepository

interface FeedbackRepository : JpaRepository<Feedback, Long>
