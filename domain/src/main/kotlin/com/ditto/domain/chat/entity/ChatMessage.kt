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

    /**
     * 이 메시지를 아직 안 읽은 참여자 수. 발신자와 이탈자는 세지 않는다.
     * 발신자 커서는 본인 메시지를 안 가리킬 수 있어 넣으면 1이 남고, 이탈자는 영영 안 읽어 숫자가 줄지 않는다.
     * SYSTEM 메시지는 0. 조회자와 무관한 값이라 REST 와 STOMP 가 같은 수를 낸다.
     */
    fun unreadCountAmong(roomMembers: Collection<ChatRoomMember>): Int {
        if (messageType == ChatMessageType.SYSTEM) {
            return 0
        }
        return roomMembers.count { it.memberId != senderId && !it.hasLeft && !it.hasRead(id) }
    }

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

        /**
         * 방에서 일어난 사건을 대화 흐름에 남긴다.
         *
         * [content]에는 완성된 문장이 아니라 사건 코드(`USER_LEFT` 등)를 넣는다 —
         * 같은 사건이라도 조회자에 따라 문구가 달라지기 때문이다(1:1에서 나간 본인에게
         * "상대방이 채팅을 종료했습니다"는 거짓이고, 그룹은 닉네임을 붙여야 한다).
         * 표시 문구는 [senderId]와 방 유형을 아는 클라이언트가 만든다.
         * 코드 목록은 `docs/domains/chat.md` 참조.
         */
        fun system(roomId: Long, senderId: Long, content: String): ChatMessage =
            of(
                roomId = roomId,
                senderId = senderId,
                content = content,
                messageType = ChatMessageType.SYSTEM,
            )
    }
}
