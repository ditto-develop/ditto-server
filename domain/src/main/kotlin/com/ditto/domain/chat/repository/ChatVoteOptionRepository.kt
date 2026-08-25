package com.ditto.domain.chat.repository

import com.ditto.domain.chat.entity.ChatVoteOption
import org.springframework.data.jpa.repository.JpaRepository

interface ChatVoteOptionRepository : JpaRepository<ChatVoteOption, Long> {

    /** 투표의 선택지 전체 — id 오름차순이 곧 입력 순이다(동표 노출 순서가 이 순서를 쓴다). */
    fun findAllByVoteIdOrderByIdAsc(voteId: Long): List<ChatVoteOption>
}
