package com.ditto.domain.quiz.repository.querydsl

interface QuizProgressRepositoryCustom {
    fun deleteByMemberIdAndQuizSetIds(memberId: Long, quizSetIds: List<Long>)
}
