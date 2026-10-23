package com.ditto.api.admin.stats.dto

import com.ditto.domain.chat.repository.querydsl.ChatRoomMemberCount
import com.ditto.domain.chat.repository.querydsl.ChatRoomSenderCount

data class ChatStatsData(
    val chatRoomMemberCounts: List<ChatRoomMemberCount>,
    val chatRoomSenderCounts: List<ChatRoomSenderCount>,
)
