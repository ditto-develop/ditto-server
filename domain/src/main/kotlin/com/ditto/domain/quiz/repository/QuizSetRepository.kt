package com.ditto.domain.quiz.repository

import com.ditto.domain.quiz.entity.QuizSet
import com.ditto.domain.quiz.repository.querydsl.QuizSetRepositoryCustom
import org.springframework.data.jpa.repository.JpaRepository

interface QuizSetRepository :
    JpaRepository<QuizSet, Long>,
    QuizSetRepositoryCustom {
    /** 어드민 목록용 — 최신 주차부터 정렬해 전체 조회. */
    fun findAllByOrderByYearDescMonthDescWeekDescIdDesc(): List<QuizSet>
}
