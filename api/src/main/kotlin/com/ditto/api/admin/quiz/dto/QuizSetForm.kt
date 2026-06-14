package com.ditto.api.admin.quiz.dto

import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QuizSet
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDateTime

/**
 * 퀴즈셋 생성·수정 폼 바인딩. datetime-local 입력은 "yyyy-MM-ddTHH:mm" 형식이다.
 */
class QuizSetForm(
    var year: Int = 0,
    var month: Int = 1,
    var week: Int = 1,
    var category: String = "",
    var title: String = "",
    var description: String? = null,
    @field:DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    var startDate: LocalDateTime? = null,
    @field:DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
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
