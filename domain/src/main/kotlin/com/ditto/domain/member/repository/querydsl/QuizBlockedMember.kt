package com.ditto.domain.member.repository.querydsl

import java.time.LocalDateTime

data class QuizBlockedMember(
    val memberId: Long,
    val nickname: String,
    val joinedAt: LocalDateTime?,
    val introNoteCount: Long,
    val hasStartedQuiz: Boolean,
)
