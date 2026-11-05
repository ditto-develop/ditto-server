package com.ditto.domain.quiz.repository

import com.ditto.domain.quiz.entity.QuizChoiceStat
import org.springframework.data.jpa.repository.JpaRepository

interface QuizChoiceStatRepository : JpaRepository<QuizChoiceStat, Long> {
    fun findByQuizSetId(quizSetId: Long): List<QuizChoiceStat>
}
