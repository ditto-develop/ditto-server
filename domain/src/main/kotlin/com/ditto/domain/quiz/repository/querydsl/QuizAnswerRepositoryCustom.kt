package com.ditto.domain.quiz.repository.querydsl

interface QuizAnswerRepositoryCustom {
    fun deleteByMemberIdAndQuizIds(memberId: Long, quizIds: List<Long>)
}
