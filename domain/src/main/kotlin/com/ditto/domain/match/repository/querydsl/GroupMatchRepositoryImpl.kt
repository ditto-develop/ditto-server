package com.ditto.domain.match.repository.querydsl

import com.ditto.domain.match.entity.GroupMatch
import com.ditto.domain.match.entity.QGroupMatch.groupMatch
import com.ditto.domain.quiz.entity.QQuizSet.quizSet
import com.querydsl.jpa.impl.JPAQueryFactory
import java.time.LocalDate
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
class GroupMatchRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : GroupMatchRepositoryCustom {

    /**
     * 마감이 지난 미성사 그룹. 퀴즈셋의 주 시작일(월요일)로 마감을 판정한다 —
     * 그룹 자체에는 시각이 없고, 채팅 개방이 그 주 금요일 00:00 이기 때문이다.
     *
     * 수락자가 0명인 그룹도 함께 나온다. 알릴 사람이 없을 뿐이라 거르는 건 부르는 쪽 몫이다.
     */
    override fun findUnformedBetween(
        oldestWeekStartedOn: LocalDate,
        lastWeekStartedOn: LocalDate,
    ): List<GroupMatch> = queryFactory
        .selectFrom(groupMatch)
        .join(quizSet).on(groupMatch.quizSetId.eq(quizSet.id))
        .where(
            groupMatch.isActive.isFalse,
            quizSet.weekStartedOn.goe(oldestWeekStartedOn),
            quizSet.weekStartedOn.loe(lastWeekStartedOn),
        )
        .fetch()
}
