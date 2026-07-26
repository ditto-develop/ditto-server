package com.ditto.domain.rematch.entity

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
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.Comment
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "rematch",
    uniqueConstraints = [
        // 같은 소스 그룹에서 같은 두 멤버의 쌍은 방향 무관 유일
        // memberId1 = min(A, B), memberId2 = max(A, B) 로 정규화하여 저장 (ADR 0008 패턴)
        UniqueConstraint(
            name = "rematch_uk_1",
            columnNames = ["source_group_match_id", "member_id_1", "member_id_2"],
        ),
    ],
)
class Rematch private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("원본 그룹 매칭 ID")
    @Column(name = "source_group_match_id", nullable = false)
    val sourceGroupMatchId: Long,

    @Comment("종료된 그룹 채팅방 ID")
    @Column(name = "source_chat_room_id", nullable = false)
    val sourceChatRoomId: Long,

    @Comment("원본 퀴즈 세트 ID")
    @Column(name = "quiz_set_id", nullable = false)
    val quizSetId: Long,

    @Comment("주간 제한 키 (Asia/Seoul 기준 해당 주 월요일)")
    @Column(name = "week_started_on", nullable = false)
    val weekStartedOn: LocalDate,

    @Comment("페어 중 작은 회원 ID (정규화)")
    @Column(name = "member_id_1", nullable = false)
    val memberId1: Long,

    @Comment("페어 중 큰 회원 ID (정규화)")
    @Column(name = "member_id_2", nullable = false)
    val memberId2: Long,
) : BaseEntity() {

    @Comment("memberId1의 재매칭 선택 (NULL=미응답)")
    @Column(name = "member_1_wants", nullable = true)
    var member1Wants: Boolean? = null
        protected set

    @Comment("memberId2의 재매칭 선택 (NULL=미응답)")
    @Column(name = "member_2_wants", nullable = true)
    var member2Wants: Boolean? = null
        protected set

    @Comment("쌍 상태")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: RematchStatus = RematchStatus.WAITING
        protected set

    @Comment("상호 선택 성사 일시")
    @Column(name = "matched_at", nullable = true)
    var matchedAt: LocalDateTime? = null
        protected set

    /** memberId 기준 페어의 상대방 ID */
    fun counterpartOf(memberId: Long): Long {
        requirePairMember(memberId)
        return if (memberId == memberId1) memberId2 else memberId1
    }

    /** memberId 본인의 확정 선택(미응답이면 null). 상대 선택은 성사 전 노출 금지라 본인 값 조회만 제공한다 */
    fun wantsOf(memberId: Long): Boolean? {
        requirePairMember(memberId)
        return if (memberId == memberId1) member1Wants else member2Wants
    }

    /**
     * memberId의 재매칭 선택을 최종 확정한다. 양쪽 응답이 모두 확정되는 호출에서
     * 상호 true면 MATCHED, 아니면 CANCELLED로 전이한다.
     * 호출 전 반드시 pair 행을 PESSIMISTIC_WRITE로 잠가야 동시 제출에서 상대 선택을 놓치지 않는다 (ADR 0011).
     */
    fun submitWants(memberId: Long, wants: Boolean, now: LocalDateTime) {
        if (status != RematchStatus.WAITING) throw WarnException(ErrorCode.REMATCH_PAIR_ALREADY_SETTLED)
        if (wantsOf(memberId) != null) throw WarnException(ErrorCode.REMATCH_ALREADY_SUBMITTED)

        if (memberId == memberId1) member1Wants = wants else member2Wants = wants
        settleIfBothSubmitted(now)
    }

    private fun settleIfBothSubmitted(now: LocalDateTime) {
        val member1Wants = member1Wants ?: return
        val member2Wants = member2Wants ?: return

        if (member1Wants && member2Wants) {
            status = RematchStatus.MATCHED
            matchedAt = now
            return
        }
        status = RematchStatus.CANCELLED
    }

    private fun requirePairMember(memberId: Long) {
        if (memberId != memberId1 && memberId != memberId2) throw WarnException(ErrorCode.NOT_REMATCH_PAIR_MEMBER)
    }

    companion object {
        fun create(
            sourceGroupMatchId: Long,
            sourceChatRoomId: Long,
            quizSetId: Long,
            weekStartedOn: LocalDate,
            memberIdA: Long,
            memberIdB: Long,
        ): Rematch {
            if (memberIdA == memberIdB) throw WarnException(ErrorCode.SELF_REMATCH_NOT_ALLOWED)
            if (weekStartedOn.dayOfWeek != DayOfWeek.MONDAY) throw WarnException(ErrorCode.INVALID_REMATCH_WEEK)

            return Rematch(
                sourceGroupMatchId = sourceGroupMatchId,
                sourceChatRoomId = sourceChatRoomId,
                quizSetId = quizSetId,
                weekStartedOn = weekStartedOn,
                memberId1 = minOf(memberIdA, memberIdB),
                memberId2 = maxOf(memberIdA, memberIdB),
            )
        }
    }
}
