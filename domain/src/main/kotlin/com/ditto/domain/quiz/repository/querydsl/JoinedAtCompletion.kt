package com.ditto.domain.quiz.repository.querydsl

import java.time.LocalDate
import java.time.LocalDateTime

data class JoinedAtCompletion(
    val joinedAt: LocalDateTime,
    val weekStartedOn: LocalDate,
)
