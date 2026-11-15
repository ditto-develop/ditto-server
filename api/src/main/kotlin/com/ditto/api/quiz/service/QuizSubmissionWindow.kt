package com.ditto.api.quiz.service

import com.ditto.api.match.SecretMatchingSchedule
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QuizSet
import java.time.LocalDateTime

/** 퀴즈셋에 지금 답을 낼 수 있는 구간. */
enum class QuizSubmissionWindow {
    /** 셋의 응답 기간(startDate~endDate) */
    REGULAR,

    /** 응답 마감 뒤 토 20:00 전. 시크릿 매칭이 켜져 있을 때 활성 1:1 셋만 */
    LATE,

    /** 토 20:00 부터 그 주 일요일까지. 4007 로 거절한다 */
    LATE_CLOSED,

    /** 시작 전, 비활성 셋, 늦은 응시가 없는 셋의 마감 뒤, 다음 주 */
    OUTSIDE,
    ;

    companion object {
        fun of(quizSet: QuizSet, now: LocalDateTime, lateSubmissionEnabled: Boolean): QuizSubmissionWindow {
            if (!quizSet.isActive || now < quizSet.startDate) return OUTSIDE
            if (now <= quizSet.endDate) return REGULAR
            if (!lateSubmissionEnabled || quizSet.matchingType != MatchingType.ONE_TO_ONE) return OUTSIDE

            val week = quizSet.operationWeek
            if (now < SecretMatchingSchedule.lateSubmissionClosesAt(week)) return LATE
            if (now in week.dateTimeRange) return LATE_CLOSED
            return OUTSIDE
        }
    }
}
