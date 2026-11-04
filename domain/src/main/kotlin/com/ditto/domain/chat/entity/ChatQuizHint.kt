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
 * 방의 첫 대화 힌트를 만들었다는 표시. 방당 하나(유니크)라 스케줄러가 겹쳐 돌아도 힌트가 두 번 생기지 않는다.
 *
 * [messageId] 없는 행(빈 표시)도 있다 — 남기지 않으면 누락 복구가 그 방을 매 주기 다시 집는다.
 * - 힌트로 고를 문항이 없었던 방(원본 매칭이 지워진 QA 방 등)
 * - 기능(`QUIZ_CHAT`)을 켜는 순간 이미 열려 대화 중이던 방 — 힌트가 대화 중간에 끼어들지 않게 한다
 */
@Entity
@Table(
    name = "chat_quiz_hint",
    uniqueConstraints = [
        UniqueConstraint(name = "chat_quiz_hint_uk_1", columnNames = ["room_id"]),
    ],
)
class ChatQuizHint private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("채팅방 ID")
    @Column(name = "room_id", nullable = false)
    val roomId: Long,

    @Comment("힌트 메시지 ID (고를 문항이 없었으면 NULL)")
    @Column(name = "message_id", nullable = true)
    val messageId: Long?,
) : BaseEntity() {

    companion object {
        fun of(roomId: Long, messageId: Long?): ChatQuizHint = ChatQuizHint(roomId = roomId, messageId = messageId)
    }
}
