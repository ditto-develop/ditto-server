package com.ditto.domain.match.entity

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
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
import org.hibernate.annotations.Comment
import java.time.LocalDateTime

@Entity
@Table(
    name = "personal_match",
    uniqueConstraints = [
        // 방향 무관하게 두 멤버 + 퀴즈셋 조합은 유일
        // memberId1 = min(A, B), memberId2 = max(A, B) 로 정규화하여 저장
        UniqueConstraint(
            name = "personal_match_uk_1",
            columnNames = ["member_id_1", "member_id_2", "quiz_set_id"],
        ),
    ],
    indexes = [
        Index(name = "personal_match_index_1", columnList = "member_id_1, quiz_set_id, status"),
        Index(name = "personal_match_index_2", columnList = "member_id_2, quiz_set_id, status"),
    ],
)
class PersonalMatch private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("페어 중 작은 회원 ID (정규화)")
    @Column(name = "member_id_1", nullable = false)
    val memberId1: Long,

    @Comment("페어 중 큰 회원 ID (정규화)")
    @Column(name = "member_id_2", nullable = false)
    val memberId2: Long,

    @Comment("요청 보낸 회원 ID")
    @Column(name = "requester_id", nullable = false)
    val requesterId: Long,

    @Comment("퀴즈 세트 ID")
    @Column(name = "quiz_set_id", nullable = false)
    val quizSetId: Long,

    status: PersonalMatchStatus = PersonalMatchStatus.PENDING,
    respondedAt: LocalDateTime? = null,
) : BaseEntity() {

    @Comment("매칭 요청 상태")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: PersonalMatchStatus = status
        protected set

    @Comment("응답일시")
    @Column(name = "responded_at", nullable = true)
    var respondedAt: LocalDateTime? = respondedAt
        protected set

    /** 요청을 받은 상대방 ID */
    fun receiverId(): Long = if (requesterId == memberId1) memberId2 else memberId1

    fun isPending(): Boolean = status == PersonalMatchStatus.PENDING

    fun accept() {
        requirePending()
        status = PersonalMatchStatus.ACCEPTED
        respondedAt = LocalDateTime.now()
    }

    fun reject() {
        requirePending()
        status = PersonalMatchStatus.REJECTED
        respondedAt = LocalDateTime.now()
    }

    fun cancel() {
        requirePending()
        status = PersonalMatchStatus.CANCELLED
        respondedAt = LocalDateTime.now()
    }

    fun expire() {
        requirePending()
        status = PersonalMatchStatus.EXPIRED
        respondedAt = LocalDateTime.now()
    }

    private fun requirePending() {
        if (!isPending()) throw WarnException(ErrorCode.INVALID_STATUS_TRANSITION)
    }

    companion object {
        fun create(
            requesterId: Long,
            receiverId: Long,
            quizSetId: Long,
        ): PersonalMatch = PersonalMatch(
            memberId1 = minOf(requesterId, receiverId),
            memberId2 = maxOf(requesterId, receiverId),
            requesterId = requesterId,
            quizSetId = quizSetId,
        )
    }
}
