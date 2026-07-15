package com.ditto.domain.chat.entity

import com.ditto.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.Comment

@Entity
@Table(
    name = "chat_room_member",
    uniqueConstraints = [
        // 같은 방에 같은 회원이 중복 참여 불가
        UniqueConstraint(
            name = "chat_room_member_uk_1",
            columnNames = ["room_id", "member_id"],
        ),
    ],
    indexes = [
        // 내 채팅방 목록 조회
        Index(name = "chat_room_member_index_1", columnList = "member_id"),
    ],
)
class ChatRoomMember private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("채팅방 ID")
    @Column(name = "room_id", nullable = false)
    val roomId: Long,

    @Comment("회원 ID")
    @Column(name = "member_id", nullable = false)
    val memberId: Long,

    lastReadMessageId: Long? = null,
) : BaseEntity() {

    @Comment("마지막으로 읽은 메시지 ID")
    @Column(name = "last_read_message_id", nullable = true)
    var lastReadMessageId: Long? = lastReadMessageId
        protected set

    /** 읽음 위치를 messageId 까지 전진시킨다. 이미 더 앞을 읽었다면 그대로 둔다(단조 증가). */
    fun readUpTo(messageId: Long) {
        val current = lastReadMessageId
        if (current == null || messageId > current) {
            lastReadMessageId = messageId
        }
    }

    companion object {
        fun of(roomId: Long, memberId: Long): ChatRoomMember =
            ChatRoomMember(roomId = roomId, memberId = memberId)
    }
}
