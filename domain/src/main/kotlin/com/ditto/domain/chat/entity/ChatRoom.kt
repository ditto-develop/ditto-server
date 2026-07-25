package com.ditto.domain.chat.entity

import com.ditto.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.Comment

@Entity
@Table(
    name = "chat_room",
    uniqueConstraints = [
        // 하나의 원본(매칭)에는 채팅방이 하나만 존재한다. (source_type, source_id) = 원본을 가리키는 다형 FK
        UniqueConstraint(
            name = "chat_room_uk_1",
            columnNames = ["source_type", "source_id"],
        ),
    ],
)
class ChatRoom private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("원본 유형 (PERSONAL, GROUP)")
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    val sourceType: ChatRoomType,

    @Comment("원본 매칭 ID (personal_match 또는 group_match 의 ID)")
    @Column(name = "source_id", nullable = false)
    val sourceId: Long,
) : BaseEntity() {

    companion object {
        fun personal(sourceId: Long): ChatRoom =
            ChatRoom(sourceType = ChatRoomType.PERSONAL, sourceId = sourceId)

        fun group(sourceId: Long): ChatRoom =
            ChatRoom(sourceType = ChatRoomType.GROUP, sourceId = sourceId)
    }
}
