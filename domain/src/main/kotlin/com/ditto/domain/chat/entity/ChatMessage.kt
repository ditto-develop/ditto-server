package com.ditto.domain.chat.entity

import com.ditto.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.Comment

@Entity
@Table(
    name = "chat_message",
    indexes = [
        // 방별 메시지 커서 페이징 (room_id + id 역순 조회)
        Index(name = "chat_message_index_1", columnList = "room_id, id"),
    ],
)
class ChatMessage private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("채팅방 ID")
    @Column(name = "room_id", nullable = false)
    val roomId: Long,

    @Comment("보낸 회원 ID")
    @Column(name = "sender_id", nullable = false)
    val senderId: Long,

    @Comment("메시지 유형 (TEXT, IMAGE, SYSTEM)")
    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    val messageType: ChatMessageType,

    @Comment("메시지 내용")
    @Column(nullable = false, length = 1000)
    val content: String,
) : BaseEntity() {

    companion object {
        fun of(
            roomId: Long,
            senderId: Long,
            content: String,
            messageType: ChatMessageType = ChatMessageType.TEXT,
        ): ChatMessage = ChatMessage(
            roomId = roomId,
            senderId = senderId,
            messageType = messageType,
            content = content,
        )
    }
}
