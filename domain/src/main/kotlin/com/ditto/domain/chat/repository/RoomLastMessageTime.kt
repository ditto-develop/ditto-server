package com.ditto.domain.chat.repository

import java.time.LocalDateTime

/** [ChatMessageRepository.findLastMessageTimes]의 한 줄. */
interface RoomLastMessageTime {
    val roomId: Long
    val lastMessageAt: LocalDateTime
}
