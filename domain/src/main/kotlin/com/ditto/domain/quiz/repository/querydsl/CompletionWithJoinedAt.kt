package com.ditto.domain.quiz.repository.querydsl

import java.time.LocalDate
import java.time.LocalDateTime

data class CompletionWithJoinedAt(
    val joinedAt: LocalDateTime,
    val weekStartedOn: LocalDate,
)
