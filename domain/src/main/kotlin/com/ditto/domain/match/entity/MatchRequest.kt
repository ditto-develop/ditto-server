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
    name = "match_request",
    uniqueConstraints = [
        UniqueConstraint(
            name = "match_request_uk_1",
            columnNames = ["from_member_id", "to_member_id", "quiz_set_id"],
        ),
    ],
    indexes = [
        Index(name = "match_request_index_1", columnList = "to_member_id, quiz_set_id, status"),
        Index(name = "match_request_index_2", columnList = "from_member_id, quiz_set_id, status"),
    ],
)
class MatchRequest private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("요청 보낸 회원 ID")
    @Column(name = "from_member_id", nullable = false)
    val fromMemberId: Long,

    @Comment("요청 받은 회원 ID")
    @Column(name = "to_member_id", nullable = false)
    val toMemberId: Long,

    @Comment("퀴즈 세트 ID")
    @Column(name = "quiz_set_id", nullable = false)
    val quizSetId: Long,

    status: MatchRequestStatus = MatchRequestStatus.PENDING,
    respondedAt: LocalDateTime? = null,
) : BaseEntity() {

    @Comment("매칭 요청 상태")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: MatchRequestStatus = status
        protected set

    @Comment("응답일시")
    @Column(name = "responded_at", nullable = true)
    var respondedAt: LocalDateTime? = respondedAt
        protected set

    fun isPending(): Boolean = status == MatchRequestStatus.PENDING

    fun accept() {
        requirePending()
        status = MatchRequestStatus.ACCEPTED
        respondedAt = LocalDateTime.now()
    }

    fun reject() {
        requirePending()
        status = MatchRequestStatus.REJECTED
        respondedAt = LocalDateTime.now()
    }

    fun cancel() {
        requirePending()
        status = MatchRequestStatus.CANCELLED
        respondedAt = LocalDateTime.now()
    }

    fun expire() {
        requirePending()
        status = MatchRequestStatus.EXPIRED
        respondedAt = LocalDateTime.now()
    }

    private fun requirePending() {
        if (!isPending()) throw WarnException(ErrorCode.INVALID_STATUS_TRANSITION)
    }

    companion object {
        fun create(
            fromMemberId: Long,
            toMemberId: Long,
            quizSetId: Long,
        ): MatchRequest = MatchRequest(
            fromMemberId = fromMemberId,
            toMemberId = toMemberId,
            quizSetId = quizSetId,
        )
    }
}
