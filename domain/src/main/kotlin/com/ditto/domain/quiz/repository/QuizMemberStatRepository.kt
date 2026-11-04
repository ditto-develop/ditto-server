package com.ditto.domain.quiz.repository

import com.ditto.domain.quiz.entity.QuizMemberStat
import org.springframework.data.jpa.repository.JpaRepository

interface QuizMemberStatRepository : JpaRepository<QuizMemberStat, Long> {
    fun findByQuizSetIdAndMemberId(quizSetId: Long, memberId: Long): QuizMemberStat?

    fun findByMemberIdAndQuizSetIdIn(memberId: Long, quizSetIds: Collection<Long>): List<QuizMemberStat>

    fun findByQuizSetId(quizSetId: Long): List<QuizMemberStat>
}
