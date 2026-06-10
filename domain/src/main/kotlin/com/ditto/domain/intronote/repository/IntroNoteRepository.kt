package com.ditto.domain.intronote.repository

import com.ditto.domain.intronote.entity.IntroNote
import com.ditto.domain.intronote.entity.IntroQuestion
import org.springframework.data.jpa.repository.JpaRepository

interface IntroNoteRepository : JpaRepository<IntroNote, Long> {

    fun findAllByMemberId(memberId: Long): List<IntroNote>

    fun findByMemberIdAndQuestion(memberId: Long, question: IntroQuestion): IntroNote?

    /** 여러 회원의 특정 질문 답변을 한 번에 조회 (후보 목록 프로필 조인 시 N+1 방지) */
    fun findByMemberIdInAndQuestion(memberIds: List<Long>, question: IntroQuestion): List<IntroNote>
}
