package com.ditto.domain.chat.repository

import com.ditto.domain.chat.entity.ChatVoteChoice
import org.springframework.data.jpa.repository.JpaRepository

interface ChatVoteChoiceRepository : JpaRepository<ChatVoteChoice, Long> {

    /** 투표의 표 전체 — 집계(선택지별 투표자)와 내 표 조회가 메모리에서 가른다. */
    fun findAllByVoteId(voteId: Long): List<ChatVoteChoice>
}
