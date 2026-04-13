package com.ditto.domain.quiz.repository.querydsl

import com.ditto.domain.quiz.entity.QuizSet
import java.time.LocalDateTime

interface QuizSetRepositoryCustom {
    fun findCurrentWeekActive(now: LocalDateTime): List<QuizSet>
}
