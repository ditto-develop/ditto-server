package com.ditto.api.admin.qa.dto

import com.ditto.domain.chat.entity.ChatEndReason
import com.ditto.domain.chat.entity.ChatMessageType
import com.ditto.domain.chat.entity.ChatRoomStatus
import com.ditto.domain.chat.entity.ChatRoomType
import java.time.LocalDateTime

/** 콘솔 목록의 한 줄. 인원은 나가지 않은 사람만 센다. */
class QaRoomSummary(
    val roomId: Long,
    val sourceType: ChatRoomType,
    val status: ChatRoomStatus,
    val opensAt: LocalDateTime,
    val expiresAt: LocalDateTime,
    val memberCount: Int,
    val dummyCount: Int,
    val lastMessageAt: LocalDateTime?,
)

class QaRoomView(
    val roomId: Long,
    val sourceType: ChatRoomType,
    val status: ChatRoomStatus,
    val opensAt: LocalDateTime,
    val expiresAt: LocalDateTime,
    val endReason: ChatEndReason?,
    val members: List<QaRoomMember>,
    val messages: List<QaRoomMessage>,
) {
    val activeDummies: List<QaMember> = members.filter { it.dummy && !it.left }.map { it.member }
}

class QaRoomMember(
    val member: QaMember,
    val dummy: Boolean,
    val left: Boolean,
    val lastReadMessageId: Long?,
)

class QaRoomMessage(
    val messageId: Long,
    val sender: QaMember,
    val fromDummy: Boolean,
    val messageType: ChatMessageType,
    val content: String,
    val sentAt: LocalDateTime,
    val unreadCount: Int,
) {
    val system: Boolean = messageType == ChatMessageType.SYSTEM
}
