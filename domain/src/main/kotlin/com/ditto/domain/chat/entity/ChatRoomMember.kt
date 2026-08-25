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
import org.hibernate.annotations.DynamicUpdate
import java.time.LocalDateTime

// 바뀐 컬럼만 UPDATE 한다 — 전 컬럼 UPDATE 면 읽음 처리(readUpTo)와 이탈(leave)이 겹칠 때
// 먼저 커밋된 left_at 을 읽음 커서 갱신이 NULL 로 되돌려 나간 사람이 참여자로 되살아난다.
@DynamicUpdate
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

    @Comment("방 이탈 시각 (참여 중이면 NULL)")
    @Column(name = "left_at")
    var leftAt: LocalDateTime? = null
        protected set

    /** 방을 나갔는지. 행을 지우지 않는 이유는 읽음 커서·과거 SYSTEM 메시지 해석을 보존하기 위해서다. */
    val hasLeft: Boolean
        get() = leftAt != null

    /** 읽음 위치를 messageId 까지 전진시킨다. 이미 더 앞을 읽었다면 그대로 둔다(단조 증가). */
    fun readUpTo(messageId: Long) {
        val current = lastReadMessageId
        if (current == null || messageId > current) {
            lastReadMessageId = messageId
        }
    }

    /**
     * 방에서 나간다. 이미 나간 멤버를 다시 내보내는 것은 호출자가 [hasLeft] 확인을 빠뜨린 것이므로
     * 조용히 넘기지 않는다 — 최초 이탈 시각이 덮이면 "언제 나갔는지"가 사라진다.
     * (재요청을 성공으로 답하는 멱등 처리는 서비스가 [hasLeft]를 먼저 보고 한다)
     */
    fun leave(at: LocalDateTime) {
        check(leftAt == null) { "이미 방을 나간 멤버입니다: id=$id, leftAt=$leftAt" }
        leftAt = at
    }

    companion object {
        fun of(roomId: Long, memberId: Long): ChatRoomMember =
            ChatRoomMember(roomId = roomId, memberId = memberId)
    }
}
