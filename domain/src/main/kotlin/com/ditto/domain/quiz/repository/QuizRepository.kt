package com.ditto.domain.quiz.repository

import com.ditto.domain.quiz.entity.Quiz
import org.springframework.data.jpa.repository.JpaRepository

interface QuizRepository : JpaRepository<Quiz, Long> {
    fun findByQuizSetIdInOrderByDisplayOrderAsc(quizSetIds: List<Long>): List<Quiz>
    fun countByQuizSetId(quizSetId: Long): Long
}
