package com.ditto.domain.quiz

import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QuizSet
import com.ditto.domain.withId
import java.time.LocalDateTime

object QuizSetFixture {

    fun create(
        year: Int = 2026,
        month: Int = 4,
        week: Int = 2,
        category: String = "성격",
        title: String = "이번 주 1:1 매칭",
        description: String? = "테스트 퀴즈 세트 설명",
        startDate: LocalDateTime = LocalDateTime.of(2026, 4, 6, 0, 0),
        endDate: LocalDateTime = LocalDateTime.of(2026, 4, 12, 23, 59, 59),
        isActive: Boolean = true,
        matchingType: MatchingType = MatchingType.ONE_TO_ONE,
        id: Long = 0L,
    ): QuizSet = QuizSet.create(
        year = year,
        month = month,
        week = week,
        category = category,
        title = title,
        description = description,
        startDate = startDate,
        endDate = endDate,
        isActive = isActive,
        matchingType = matchingType,
    ).withId(id)
}
