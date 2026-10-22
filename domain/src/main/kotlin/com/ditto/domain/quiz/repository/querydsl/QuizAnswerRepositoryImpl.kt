package com.ditto.domain.quiz.repository.querydsl

import com.ditto.domain.member.entity.QMember.member
import com.ditto.domain.member.repository.querydsl.MemberFilter
import com.ditto.domain.member.repository.querydsl.nonAdminMatching
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QQuiz.quiz
import com.ditto.domain.quiz.entity.QQuizAnswer.quizAnswer
import com.ditto.domain.quiz.entity.QQuizChoice.quizChoice
import com.ditto.domain.quiz.entity.QQuizSet.quizSet
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

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

    // 회원 조건을 left join 의 on 에 둬야 조건에 안 맞는 답만 빠지고 선택지 행은 남는다.
    override fun countAnswersPerChoiceInWeek(
        weekStartedOn: LocalDate,
        matchingType: MatchingType?,
        filter: MemberFilter,
    ): List<ChoiceAnswerCount> {
        val answerCountExpression = member.id.count()
        return queryFactory
            .select(
                quizSet.id,
                quizSet.matchingType,
                quiz.id,
                quiz.displayOrder,
                quiz.question,
                quizChoice.id,
                quizChoice.displayOrder,
                quizChoice.content,
                answerCountExpression,
            )
            .from(quizChoice)
            .join(quiz).on(quiz.id.eq(quizChoice.quizId))
            .join(quizSet).on(quizSet.id.eq(quiz.quizSetId), quizSet.isActive.isTrue)
            .leftJoin(quizAnswer).on(quizAnswer.choiceId.eq(quizChoice.id))
            .leftJoin(member).on(member.id.eq(quizAnswer.memberId), member.nonAdminMatching(filter))
            .where(quizSet.weekStartedOn.eq(weekStartedOn), matchingType?.let { quizSet.matchingType.eq(it) })
            .groupBy(
                quizSet.id,
                quizSet.matchingType,
                quiz.id,
                quiz.displayOrder,
                quiz.question,
                quizChoice.id,
                quizChoice.displayOrder,
                quizChoice.content,
            )
            .orderBy(quizSet.id.asc(), quiz.displayOrder.asc(), quizChoice.displayOrder.asc())
            .fetch()
            .map { row ->
                ChoiceAnswerCount(
                    quizSetId = row.get(quizSet.id) ?: throw IllegalStateException("group by 키 quizSetId 가 비었다"),
                    matchingType = row.get(quizSet.matchingType)
                        ?: throw IllegalStateException("group by 키 matchingType 이 비었다"),
                    quizId = row.get(quiz.id) ?: throw IllegalStateException("group by 키 quizId 가 비었다"),
                    quizDisplayOrder = row.get(quiz.displayOrder) ?: 0,
                    question = row.get(quiz.question).orEmpty(),
                    choiceId = row.get(quizChoice.id) ?: throw IllegalStateException("group by 키 choiceId 가 비었다"),
                    choiceDisplayOrder = row.get(quizChoice.displayOrder) ?: 0,
                    choiceContent = row.get(quizChoice.content).orEmpty(),
                    answerCount = row.get(answerCountExpression) ?: 0L,
                )
            }
    }
}
