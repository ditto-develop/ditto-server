package com.ditto.domain.quiz.repository

import com.ditto.domain.quiz.entity.QuizSet
import com.ditto.domain.quiz.repository.querydsl.QuizSetRepositoryCustom
import org.springframework.data.jpa.repository.JpaRepository

interface QuizSetRepository :
    JpaRepository<QuizSet, Long>,
    QuizSetRepositoryCustom
