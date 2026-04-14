package com.ditto.domain.quiz.repository.querydsl

import com.ditto.domain.quiz.entity.QQuizProgress.quizProgress
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.transaction.annotation.Transactional

@Transactional
class QuizProgressRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : QuizProgressRepositoryCustom {

    override fun deleteByMemberIdAndQuizSetIds(memberId: Long, quizSetIds: List<Long>) {
        if (quizSetIds.isEmpty()) return
        queryFactory
            .delete(quizProgress)
            .where(
                quizProgress.memberId.eq(memberId),
                quizProgress.quizSetId.`in`(quizSetIds),
            )
            .execute()
    }
}
