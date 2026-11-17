package com.ditto.domain.quiz.repository.querydsl

import com.ditto.domain.quiz.entity.QQuizProgress
import com.ditto.domain.quiz.entity.QQuizSet
import com.querydsl.core.types.dsl.BooleanExpression

/**
 * 응답 마감까지 완주했는가(정규 매칭). 엔티티의 `QuizProgress.isCompletedOnTime`과 같은 규칙이다.
 * 완주 시각이 없는 예전 행과 더미는 마감 전 완주로 보고, 진행 중인 행도 시각이 없어 통과한다.
 */
internal fun QQuizProgress.isCompletedOnTime(quizSet: QQuizSet): BooleanExpression =
    completedAt.isNull.or(completedAt.loe(quizSet.endDate))
