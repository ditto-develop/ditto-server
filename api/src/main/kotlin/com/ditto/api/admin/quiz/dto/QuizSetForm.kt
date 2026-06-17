package com.ditto.api.admin.quiz.dto

import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QuizSet
import java.time.LocalDateTime

/**
 * 퀴즈셋 생성·수정 폼 바인딩(스프링 MVC 폼 백킹 빈이라 주생성자는 public 으로 둔다 — 바인딩 시 스프링이 인스턴스화).
 * datetime-local 입력은 ISO_LOCAL_DATE_TIME("yyyy-MM-ddTHH:mm") 이라 스프링 기본 변환으로 바인딩된다.
 */
class QuizSetForm(
    var year: Int = 0,
    var month: Int = 1,
    var week: Int = 1,
    var category: String = "",
    var title: String = "",
    var description: String? = null,
    var startDate: LocalDateTime? = null,
    var endDate: LocalDateTime? = null,
    var matchingType: MatchingType = MatchingType.ONE_TO_ONE,
    var isActive: Boolean = false,
) {
    companion object {
        fun from(quizSet: QuizSet) = QuizSetForm(
            year = quizSet.year,
            month = quizSet.month,
            week = quizSet.week,
            category = quizSet.category,
            title = quizSet.title,
            description = quizSet.description,
            startDate = quizSet.startDate,
            endDate = quizSet.endDate,
            matchingType = quizSet.matchingType,
            isActive = quizSet.isActive,
        )
    }
}
