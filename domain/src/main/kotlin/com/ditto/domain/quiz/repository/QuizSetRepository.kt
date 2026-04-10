package com.ditto.domain.quiz.repository

import com.ditto.domain.quiz.entity.QuizSet
import org.springframework.data.jpa.repository.JpaRepository

interface QuizSetRepository : JpaRepository<QuizSet, Long> {
    fun findByYearAndMonthAndWeekAndIsActiveTrue(year: Int, month: Int, week: Int): List<QuizSet>
}
