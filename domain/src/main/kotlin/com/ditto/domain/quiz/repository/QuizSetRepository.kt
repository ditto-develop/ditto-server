package com.ditto.domain.quiz.repository

import com.ditto.domain.quiz.entity.QuizSet
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

interface QuizSetRepository : JpaRepository<QuizSet, Long> {
    fun findByYearAndMonthAndWeekAndIsActiveTrue(year: Int, month: Int, week: Int): List<QuizSet>

    @Query("SELECT qs FROM QuizSet qs WHERE qs.startDate <= :now AND qs.endDate >= :now AND qs.isActive = true")
    fun findCurrentWeekActive(now: LocalDateTime): List<QuizSet>
}
