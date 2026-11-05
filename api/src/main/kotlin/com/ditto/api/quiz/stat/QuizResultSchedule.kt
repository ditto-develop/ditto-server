package com.ditto.api.quiz.stat

import com.ditto.domain.quiz.entity.QuizSet
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * 퀴즈셋 결과(비율·소수 의견·겹친 사람)가 공개되는 시각 — 응답 마감 뒤 첫 자정. 지금 규칙(월 00:00 ~ 수 23:59:59)이면 목 00:00 이다.
 *
 * 자정으로 올리는 이유: 마감 시각이 23:59:59(.999…)라 그대로 쓰면 화면이 "23:59:59 공개"를 그린다.
 * 마감이 정확히 자정이면 그 시각 그대로다.
 */
object QuizResultSchedule {

    fun opensAt(quizSet: QuizSet): LocalDateTime {
        val end = quizSet.endDate
        val midnight = end.truncatedTo(ChronoUnit.DAYS)
        return if (end == midnight) end else midnight.plusDays(1)
    }

    fun isOpen(quizSet: QuizSet, now: LocalDateTime): Boolean = now >= opensAt(quizSet)
}
