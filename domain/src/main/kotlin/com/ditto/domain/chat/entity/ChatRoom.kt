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
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime
import org.hibernate.annotations.Comment

@Entity
@Table(
    name = "chat_room",
    uniqueConstraints = [
        // 하나의 원본(매칭)에는 채팅방이 하나만 존재한다.
        UniqueConstraint(
            name = "chat_room_uk_1",
            columnNames = ["room_type", "source_id"],
        ),
    ],
    indexes = [
        // 만료 스케줄러가 끝낼 방을 찾는 경로
        Index(name = "chat_room_index_1", columnList = "status, expires_at"),
    ],
)
class ChatRoom private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("채팅방 유형 (PERSONAL, GROUP)")
    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", nullable = false, length = 20)
    val roomType: ChatRoomType,

    @Comment("원본 매칭 ID (personal_match 또는 group_match 의 ID)")
    @Column(name = "source_id", nullable = false)
    val sourceId: Long,

    @Comment("채팅 개방 시각")
    @Column(name = "opens_at", nullable = false)
    val opensAt: LocalDateTime,

    expiresAt: LocalDateTime,
    status: ChatRoomStatus,
) : BaseEntity() {

    /** 자동 종료 예정 시각. 연장(#121)으로 뒤로 밀릴 수 있어 불변이 아니다. */
    @Comment("자동 종료 예정 시각 (연장 시 이동)")
    @Column(name = "expires_at", nullable = false)
    var expiresAt: LocalDateTime = expiresAt
        protected set

    @Comment("방 상태 (SCHEDULED, ACTIVE, ENDED)")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ChatRoomStatus = status
        protected set

    @Comment("실제 종료 시각 (종료 전 NULL)")
    @Column(name = "ended_at")
    var endedAt: LocalDateTime? = null
        protected set

    @Comment("종료 사유 (EXPIRED, USER_ENDED)")
    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 20)
    var endReason: ChatEndReason? = null
        protected set

    val isEnded: Boolean
        get() = status == ChatRoomStatus.ENDED

    /**
     * 방을 종료한다. 이미 끝난 방이면 아무것도 하지 않고 `false`를 돌려준다 —
     * 만료 스케줄러·사용자·어드민이 동시에 종료를 시도해도 종료 결과가 하나로 수렴해야 하기 때문이다.
     * 호출자는 반환값으로 "이번 호출이 실제로 끝냈는지"를 판별해 후속 처리(평가 생성)를 한 번만 수행한다.
     *
     * 누가 끝냈는지는 여기 저장하지 않는다 — 나갈 때 남기는 SYSTEM 메시지의 `senderId`가 그 사실을 들고 있고,
     * 조회자는 그 값으로 "상대방이 종료했다"를 판별한다.
     */
    fun end(reason: ChatEndReason, endedAt: LocalDateTime): Boolean {
        if (isEnded) {
            return false
        }
        status = ChatRoomStatus.ENDED
        this.endedAt = endedAt
        this.endReason = reason
        return true
    }

    /** 개방 시각이 지난 예약 방을 연다. 이미 열렸거나 끝난 방은 그대로 둔다. */
    fun openIfDue(at: LocalDateTime): Boolean {
        if (status != ChatRoomStatus.SCHEDULED || at < opensAt) {
            return false
        }
        status = ChatRoomStatus.ACTIVE
        return true
    }

    companion object {
        fun personal(sourceId: Long, period: ChatPeriod, now: LocalDateTime): ChatRoom =
            of(ChatRoomType.PERSONAL, sourceId, period, now)

        fun group(sourceId: Long, period: ChatPeriod, now: LocalDateTime): ChatRoom =
            of(ChatRoomType.GROUP, sourceId, period, now)

        private fun of(
            roomType: ChatRoomType,
            sourceId: Long,
            period: ChatPeriod,
            now: LocalDateTime,
        ): ChatRoom = ChatRoom(
            roomType = roomType,
            sourceId = sourceId,
            opensAt = period.opensAt,
            expiresAt = period.expiresAt,
            status = if (period.isOpenedAt(now)) ChatRoomStatus.ACTIVE else ChatRoomStatus.SCHEDULED,
        )
    }
}
