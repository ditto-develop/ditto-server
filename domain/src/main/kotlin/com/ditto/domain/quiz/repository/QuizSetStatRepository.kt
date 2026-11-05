package com.ditto.domain.quiz.repository

import com.ditto.domain.quiz.entity.QuizSetStat
import org.springframework.data.jpa.repository.JpaRepository

interface QuizSetStatRepository : JpaRepository<QuizSetStat, Long> {
    fun findByQuizSetId(quizSetId: Long): QuizSetStat?
}
