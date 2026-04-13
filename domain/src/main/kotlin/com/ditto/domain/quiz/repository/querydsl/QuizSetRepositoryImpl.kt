package com.ditto.domain.quiz.repository.querydsl

import com.ditto.domain.quiz.entity.QQuizSet.quizSet
import com.ditto.domain.quiz.entity.QuizSet
import com.querydsl.jpa.impl.JPAQueryFactory
import java.time.LocalDateTime

class QuizSetRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : QuizSetRepositoryCustom {

    override fun findCurrentWeekActive(now: LocalDateTime): List<QuizSet> =
        queryFactory
            .selectFrom(quizSet)
            .where(
                quizSet.startDate.loe(now),
                quizSet.endDate.goe(now),
                quizSet.isActive.isTrue,
            )
            .fetch()
}
