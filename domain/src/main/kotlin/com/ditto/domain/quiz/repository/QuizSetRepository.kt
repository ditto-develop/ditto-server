package com.ditto.domain.quiz.repository

import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QuizSet
import com.ditto.domain.quiz.repository.querydsl.QuizSetRepositoryCustom
import java.time.LocalDate
import org.springframework.data.jpa.repository.JpaRepository

interface QuizSetRepository :
    JpaRepository<QuizSet, Long>,
    QuizSetRepositoryCustom {
    /** 어드민 목록용 — 최신 운영 주부터 정렬해 전체 조회. */
    fun findAllByOrderByWeekStartedOnDescIdDesc(): List<QuizSet>

    /**
     * 같은 주차·타입에 자기 말고 활성 셋이 또 있는지. 아직 저장 전인 셋은 [id]가 0이라 자기 자신이 걸릴 일이 없다.
     */
    fun existsByWeekStartedOnAndMatchingTypeAndIsActiveTrueAndIdNot(
        weekStartedOn: LocalDate,
        matchingType: MatchingType,
        id: Long,
    ): Boolean
}
