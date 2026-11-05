package com.ditto.domain.chat.entity

import com.ditto.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.Comment

/**
 * 방에 올라간 질문 카드. 한 방에 같은 문항은 한 번만 올린다(유니크) — "이미 올라온 질문"을 막고,
 * 답변 비교 화면이 문항별 '보냄' 상태를 이 표로 그린다. 카드 내용 자체는 [messageId]의 메시지에 있다.
 */
@Entity
@Table(
    name = "chat_quiz_card",
    uniqueConstraints = [
        UniqueConstraint(name = "chat_quiz_card_uk_1", columnNames = ["room_id", "quiz_id"]),
    ],
)
class ChatQuizCard private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("채팅방 ID")
    @Column(name = "room_id", nullable = false)
    val roomId: Long,

    @Comment("퀴즈 ID")
    @Column(name = "quiz_id", nullable = false)
    val quizId: Long,

    @Comment("카드 메시지 ID (chat_message.id)")
    @Column(name = "message_id", nullable = false)
    val messageId: Long,

    @Comment("보낸 회원 ID")
    @Column(name = "sender_id", nullable = false)
    val senderId: Long,
) : BaseEntity() {

    companion object {
        fun of(roomId: Long, quizId: Long, messageId: Long, senderId: Long): ChatQuizCard =
            ChatQuizCard(roomId = roomId, quizId = quizId, messageId = messageId, senderId = senderId)
    }
}
