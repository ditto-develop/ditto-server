package com.ditto.domain.chat.repository.querydsl

import com.ditto.domain.member.entity.Gender
import java.time.LocalDateTime

data class ChatRoomMessageSummary(
    val roomId: Long,
    val opensAt: LocalDateTime,
    val senderGender: Gender?,
    val messageCount: Long,
    val firstMessageAt: LocalDateTime,
    val lastMessageAt: LocalDateTime,
)
