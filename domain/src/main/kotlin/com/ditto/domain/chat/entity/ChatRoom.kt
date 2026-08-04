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
            columnNames = ["source_type", "source_id"],
        ),
    ],
    indexes = [
        // 만료 스케줄러가 끝낼 방을 찾는 경로
        Index(name = "chat_room_index_1", columnList = "status, expires_at"),
        // 평가 누락 복구가 "끝났는데 평가 없는 방"을 찾는 경로
        Index(name = "chat_room_index_2", columnList = "status, ended_at"),
    ],
)
class ChatRoom private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("원본 유형 (PERSONAL, GROUP, REMATCH)")
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    val sourceType: ChatRoomType,

    @Comment("원본 매칭 ID (personal_match, group_match 또는 rematch 의 ID)")
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
     * 참여자 한 사람의 요청으로 끝낼 수 있는 방인지. 두 사람만 있는 방(일반 1:1·재매칭)이 그렇다.
     *
     * 그룹은 한 명이 나가도 남은 사람들의 대화가 이어져야 하므로 한 사람이 끝내지 못한다
     * (멤버 이탈은 별도 트랙). 그래서 판정을 유형 비교로 흘리지 않고 이 이름으로 고정한다 —
     * 두 사람 방이 늘어날 때 호출자를 고치지 않아도 된다.
     */
    fun canEndByUser(): Boolean = sourceType != ChatRoomType.GROUP

    /** 기한이 지나 마감한다. 이미 끝난 방이면 [isEnded]로 걸러낸 뒤 호출해야 한다. */
    fun expire(at: LocalDateTime) = end(ChatEndReason.EXPIRED, at)

    /**
     * 참여자가 직접 종료한다. 호출 전에 [canEndByUser]와 [isEnded]로 걸러야 한다.
     *
     * 누가 끝냈는지는 여기 저장하지 않는다 — 나갈 때 남기는 SYSTEM 메시지의 `senderId`가 그 사실을 들고 있고,
     * 조회자는 그 값으로 "상대방이 종료했다"를 판별한다.
     */
    fun endByUser(at: LocalDateTime) = end(ChatEndReason.USER_ENDED, at)

    /**
     * 사유를 밖에서 받지 않는 이유: 종료 경로와 사유가 어긋나는 조합을 만들 수 없게 하려는 것이다.
     * 사유가 늘면(상대 차단 종료·그룹 인원 미달 해체) 이름 있는 메서드를 하나 더 둔다.
     *
     * 이미 끝난 방을 다시 끝내려는 것은 호출자가 [isEnded] 확인을 빠뜨린 것이므로 조용히 넘기지 않는다.
     * 그대로 두면 최초 종료 시각·사유가 덮여 "언제 왜 끝났는지"가 사라진다.
     */
    private fun end(reason: ChatEndReason, at: LocalDateTime) {
        check(!isEnded) { "이미 종료된 채팅방입니다: id=$id, endedAt=$endedAt" }
        status = ChatRoomStatus.ENDED
        endedAt = at
        endReason = reason
    }

    /**
     * 예약된 방을 연다. 개방 대상인지는 호출 전에 가려야 한다 — 조건에 맞지 않으면 조용히 넘기지 않는다.
     * 이미 열렸거나 끝난 방을 다시 여는 것은 상태를 거꾸로 돌리는 일이라 버그로 드러내는 편이 낫다.
     */
    fun open(at: LocalDateTime) {
        check(status == ChatRoomStatus.SCHEDULED) { "예약 상태가 아닌 채팅방입니다: id=$id, status=$status" }
        check(at >= opensAt) { "개방 시각 전입니다: id=$id, opensAt=$opensAt, at=$at" }
        status = ChatRoomStatus.ACTIVE
    }

    companion object {
        fun personal(sourceId: Long, period: ChatPeriod, now: LocalDateTime): ChatRoom =
            of(ChatRoomType.PERSONAL, sourceId, period, now)

        fun group(sourceId: Long, period: ChatPeriod, now: LocalDateTime): ChatRoom =
            of(ChatRoomType.GROUP, sourceId, period, now)

        /**
         * 그룹 재매칭으로 성사된 두 사람의 방. [sourceId]는 `rematch.id`다.
         *
         * 성사와 개방 시각이 떨어져 있어(성사는 평가 제출 직후, 개방은 그 주 금요일) 대개 `SCHEDULED`로
         * 만들어진다. 주말 도중에 성사됐다면 [ChatPeriod.isOpenedAt]이 곧바로 참이라 `ACTIVE`로 만들어져
         * 남은 시간 동안 제공된다.
         */
        fun rematch(sourceId: Long, period: ChatPeriod, now: LocalDateTime): ChatRoom =
            of(ChatRoomType.REMATCH, sourceId, period, now)

        private fun of(
            sourceType: ChatRoomType,
            sourceId: Long,
            period: ChatPeriod,
            now: LocalDateTime,
        ): ChatRoom = ChatRoom(
            sourceType = sourceType,
            sourceId = sourceId,
            opensAt = period.opensAt,
            expiresAt = period.expiresAt,
            status = if (period.isOpenedAt(now)) ChatRoomStatus.ACTIVE else ChatRoomStatus.SCHEDULED,
        )
    }
}
