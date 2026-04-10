package com.ditto.domain.quiz.repository

import com.ditto.domain.quiz.entity.QuizChoice
import org.springframework.data.jpa.repository.JpaRepository

interface QuizChoiceRepository : JpaRepository<QuizChoice, Long> {
    fun findByQuizIdInOrderByDisplayOrderAsc(quizIds: List<Long>): List<QuizChoice>
}
