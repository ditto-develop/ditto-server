package com.ditto.domain.quiz.repository.querydsl

import com.ditto.domain.match.entity.QGroupMatch.groupMatch
import com.ditto.domain.match.entity.QMatchCandidate.matchCandidate
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QQuizProgress.quizProgress
import com.ditto.domain.quiz.entity.QQuizSet.quizSet
import com.ditto.domain.quiz.entity.QuizProgressStatus
import com.ditto.domain.quiz.entity.QuizSet
import com.querydsl.jpa.impl.JPAQueryFactory
import java.time.LocalDate
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

    override fun findEndedQuizSetsWithoutCandidates(endedAfter: LocalDateTime, now: LocalDateTime): List<QuizSet> =
        queryFactory
            .selectFrom(quizSet)
            .leftJoin(matchCandidate).on(matchCandidate.quizSetId.eq(quizSet.id))
            .leftJoin(groupMatch).on(groupMatch.quizSetId.eq(quizSet.id))
            .where(
                quizSet.endDate.gt(endedAfter),
                quizSet.endDate.lt(now),
                // 후보가 하나도 없는(아직 계산 안 된) 셋만. 후보를 담는 테이블이 타입마다 달라 둘 다 본다 —
                // 그룹만 보고 빠뜨리면 그룹 퀴즈셋이 매주 다시 계산돼 후보 ID가 갈린다.
                matchCandidate.id.isNull,
                groupMatch.id.isNull,
            )
            .fetch()

    override fun findCompletedQuizSetInWeek(
        memberId: Long,
        matchingType: MatchingType,
        weekStartedOn: LocalDate,
    ): QuizSet? =
        queryFactory
            .select(quizSet)
            .from(quizProgress)
            .join(quizSet).on(quizProgress.quizSetId.eq(quizSet.id))
            .where(
                quizProgress.memberId.eq(memberId),
                quizProgress.status.eq(QuizProgressStatus.COMPLETED),
                quizSet.matchingType.eq(matchingType),
                quizSet.weekStartedOn.eq(weekStartedOn),
                // 비활성 셋은 그 주의 대표가 아니다. 활성은 주차·타입당 하나로 강제된다(AdminQuizService).
                quizSet.isActive.isTrue,
            )
            // 강제 이전에 만들어진 활성 셋이 둘 남아 있을 수 있어 id 로 마지막 순서를 정한다.
            // endDate 만으로는 못 정한다 — 같은 주 셋은 기간이 월~수로 고정돼 값이 늘 같다.
            .orderBy(quizSet.endDate.desc(), quizSet.id.desc())
            .fetchFirst()
}
