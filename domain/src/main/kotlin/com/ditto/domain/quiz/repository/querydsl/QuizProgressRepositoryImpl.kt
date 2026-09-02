package com.ditto.domain.quiz.repository.querydsl

import com.ditto.domain.quiz.entity.QQuizProgress
import com.ditto.domain.quiz.entity.QQuizProgress.quizProgress
import com.ditto.domain.quiz.entity.QuizProgressStatus
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

    override fun findLatestQuizSetIdCompletedByBoth(memberId: Long, otherMemberId: Long): Long? {
        val otherProgress = QQuizProgress("otherProgress")

        return queryFactory
            .select(quizProgress.quizSetId)
            .from(quizProgress)
            .join(otherProgress)
            .on(
                otherProgress.quizSetId.eq(quizProgress.quizSetId),
                otherProgress.memberId.eq(otherMemberId),
                otherProgress.status.eq(QuizProgressStatus.COMPLETED),
            )
            .where(
                quizProgress.memberId.eq(memberId),
                quizProgress.status.eq(QuizProgressStatus.COMPLETED),
            )
            .orderBy(quizProgress.quizSetId.desc())
            .fetchFirst()
    }
}
