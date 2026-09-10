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

    override fun countAnswersPerQuiz(quizIds: List<Long>): Map<Long, Long> {
        if (quizIds.isEmpty()) return emptyMap()

        val answerCount = quizAnswer.count()
        
        return queryFactory
            .select(quizAnswer.quizId, answerCount)
            .from(quizAnswer)
            .where(quizAnswer.quizId.`in`(quizIds))
            .groupBy(quizAnswer.quizId)
            .fetch()
            .associate { row ->
                val quizId = row.get(quizAnswer.quizId) ?: throw IllegalStateException("group by 키 quizId 가 비었다")
                quizId to (row.get(answerCount) ?: 0L)
            }
    }
}
