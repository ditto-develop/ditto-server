package com.ditto.domain.quiz.repository.querydsl

import com.ditto.domain.match.entity.QMatchCandidate.matchCandidate
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

    override fun findEndedQuizSetsWithoutCandidates(now: LocalDateTime): List<QuizSet> =
        queryFactory
            .selectFrom(quizSet)
            .leftJoin(matchCandidate).on(matchCandidate.quizSetId.eq(quizSet.id))
            .where(
                quizSet.endDate.lt(now),
                matchCandidate.id.isNull, // 후보가 하나도 없는(아직 계산 안 된) 셋만
            )
            .fetch()
}
