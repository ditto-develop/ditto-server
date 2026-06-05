package com.ditto.domain.intronote.repository

import com.ditto.domain.intronote.entity.IntroNote
import com.ditto.domain.intronote.entity.IntroQuestion
import org.springframework.data.jpa.repository.JpaRepository

interface IntroNoteRepository : JpaRepository<IntroNote, Long> {

    fun findAllByMemberId(memberId: Long): List<IntroNote>

    fun findByMemberIdAndQuestion(memberId: Long, question: IntroQuestion): IntroNote?
}
