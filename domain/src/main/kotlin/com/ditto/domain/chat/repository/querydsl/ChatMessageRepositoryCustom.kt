package com.ditto.domain.chat.repository.querydsl

import com.ditto.domain.chat.entity.ChatMessage

interface ChatMessageRepositoryCustom {

    /**
     * 방의 메시지를 최신순(id DESC)으로 size 개 조회한다.
     * cursor 가 주어지면 그 id 미만(더 과거)만 조회 — 위로 스크롤 페이징용.
     */
    fun findByRoomIdWithCursor(roomId: Long, cursor: Long?, size: Int): List<ChatMessage>
}
