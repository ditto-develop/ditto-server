package com.ditto.domain.quiz

import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QuizSet
import com.ditto.domain.system.OperationWeek
import com.ditto.domain.withId
import java.time.LocalDate
import java.time.LocalDateTime

object QuizSetFixture {

    fun create(
        category: String = "성격",
        title: String = "이번 주 1:1 매칭",
        description: String? = "테스트 퀴즈 세트 설명",
        startDate: LocalDateTime = LocalDateTime.of(2026, 4, 6, 0, 0),
        endDate: LocalDateTime = LocalDateTime.of(2026, 4, 12, 23, 59, 59),
        isActive: Boolean = true,
        matchingType: MatchingType = MatchingType.ONE_TO_ONE,
        id: Long = 0L,
    ): QuizSet = QuizSet.create(
        category = category,
        title = title,
        description = description,
        startDate = startDate,
        endDate = endDate,
        isActive = isActive,
        matchingType = matchingType,
    ).withId(id)

    /**
     * **이번 운영 주**(월 00:00 ~ 수 23:59)에 걸친 퀴즈셋.
     *
     * 매칭 후보 조회·성사 전 열람 권한·응답 경로가 모두 "이번 주 퀴즈셋"을 기준으로 판정하므로
     * (`MatchWeekPolicy`), 그 경로를 태우는 테스트는 고정 날짜 대신 이 팩토리를 쓴다.
     */
    fun currentWeek(
        matchingType: MatchingType = MatchingType.ONE_TO_ONE,
        title: String = "이번 주 퀴즈",
        isActive: Boolean = true,
        id: Long = 0L,
    ): QuizSet {
        val monday = OperationWeek.containing(LocalDate.now()).startedOn
        return create(
            title = title,
            startDate = monday.atStartOfDay(),
            endDate = monday.plusDays(2).atTime(23, 59, 59),
            isActive = isActive,
            matchingType = matchingType,
            id = id,
        )
    }
}
