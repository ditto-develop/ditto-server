package com.ditto.domain.quiz.repository.querydsl

import com.ditto.domain.quiz.entity.QQuizAnswer.quizAnswer
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.transaction.annotation.Transactional

@Transactional
class QuizAnswerRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : QuizAnswerRepositoryCustom {

    override fun deleteByMemberIdAndQuizIds(memberId: Long, quizIds: List<Long>) {
        if (quizIds.isEmpty()) return
        queryFactory
            .delete(quizAnswer)
            .where(
                quizAnswer.memberId.eq(memberId),
                quizAnswer.quizId.`in`(quizIds),
            )
            .execute()
    }
}
