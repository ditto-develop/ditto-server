package com.ditto.domain.rematch.entity

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.BaseEntity
import com.ditto.domain.system.OperationWeek
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
    indexes = [
        // 방 예약 스케줄러가 "성사됐는데 방 없는 쌍"을 찾는 경로
        Index(name = "rematch_index_1", columnList = "status, matched_at"),
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

    // 두 선택은 public 접근자를 두지 않는다. 게터가 열려 있으면 엔티티를 그대로 응답에 매핑하거나
    // 로깅하는 것만으로 상대 선택이 새기 때문에, 외부 조회는 본인 값만 주는 wantsOf()로 강제한다.
    @Comment("memberId1의 재매칭 선택 (NULL=미응답)")
    @Column(name = "member_1_wants", nullable = true)
    private var member1Wants: Boolean? = null

    @Comment("memberId2의 재매칭 선택 (NULL=미응답)")
    @Column(name = "member_2_wants", nullable = true)
    private var member2Wants: Boolean? = null

    @Comment("쌍 상태")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: RematchStatus = RematchStatus.WAITING
        protected set

    // status 와 함께만 의미를 갖는 값이라 직접 노출하지 않는다 — 조회는 matchedAt() 으로 한다.
    @Comment("상호 선택 성사 일시")
    @Column(name = "matched_at", nullable = true)
    private var matchedAt: LocalDateTime? = null

    /** 이 재매칭이 속한 운영 주. */
    val operationWeek: OperationWeek
        get() = OperationWeek(weekStartedOn)

    /** 상호 성사된 시각. 성사되지 않았으면 `null` — 성사 상태와 시각이 함께 움직인다는 불변식을 여기서 지킨다. */
    fun matchedAt(): LocalDateTime? = matchedAt.takeIf { status == RematchStatus.MATCHED }

    /** memberId 기준 페어의 상대방 ID */
    fun counterpartOf(memberId: Long): Long {
        validatePairMember(memberId)
        return if (memberId == memberId1) memberId2 else memberId1
    }

    /** memberId 본인이 확정한 선택이 주어진 값과 같은지. 재제출이 기존 확정과 일치하는지 판정할 때 쓴다. */
    fun hasSameWants(memberId: Long, wants: Boolean): Boolean = wantsOf(memberId) == wants

    /** memberId 본인의 확정 선택(미응답이면 null). 상대 선택은 성사 전 노출 금지라 본인 값 조회만 제공한다 */
    fun wantsOf(memberId: Long): Boolean? {
        validatePairMember(memberId)
        return if (memberId == memberId1) member1Wants else member2Wants
    }

    /**
     * memberId의 재매칭 선택을 최종 확정한다. 양쪽 응답이 모두 확정되는 호출에서
     * 상호 true면 MATCHED, 아니면 CANCELLED로 전이한다.
     * 호출 전 반드시 pair 행을 PESSIMISTIC_WRITE로 잠가야 동시 제출에서 상대 선택을 놓치지 않는다 (ADR 0011).
     */
    fun submitWants(memberId: Long, wants: Boolean, now: LocalDateTime) {
        // 검사 순서가 곧 비공개 계약이다. 쌍 상태를 먼저 보면 실패 응답의 오류 코드 차이만으로
        // 상대가 언제 제출했는지가 드러나므로, 인가와 본인 상태를 항상 먼저 확인한다.
        validatePairMember(memberId)
        if (wantsOf(memberId) != null) throw WarnException(ErrorCode.REMATCH_ALREADY_SUBMITTED)
        // 양쪽이 모두 제출해야 WAITING을 벗어나므로 위 가드를 지난 시점에는 항상 WAITING이다.
        // 주간 제한(R2)·탈퇴(D1)가 외부에서 취소를 걸기 시작하면 그 경로를 막는 가드가 된다.
        if (status != RematchStatus.WAITING) throw WarnException(ErrorCode.REMATCH_PAIR_ALREADY_SETTLED)

        if (memberId == memberId1) member1Wants = wants else member2Wants = wants
        settleIfBothSubmitted(now)
    }

    private fun settleIfBothSubmitted(now: LocalDateTime) {
        val firstWants = member1Wants ?: return
        val secondWants = member2Wants ?: return

        if (firstWants && secondWants) {
            status = RematchStatus.MATCHED
            matchedAt = now
            return
        }
        status = RematchStatus.CANCELLED
    }

    private fun validatePairMember(memberId: Long) {
        if (memberId != memberId1 && memberId != memberId2) throw WarnException(ErrorCode.NOT_REMATCH_PAIR_MEMBER)
    }

    companion object {
        fun create(
            sourceGroupMatchId: Long,
            sourceChatRoomId: Long,
            quizSetId: Long,
            week: OperationWeek,
            memberIdA: Long,
            memberIdB: Long,
        ): Rematch {
            if (memberIdA == memberIdB) throw WarnException(ErrorCode.SELF_REMATCH_NOT_ALLOWED)

            return Rematch(
                sourceGroupMatchId = sourceGroupMatchId,
                sourceChatRoomId = sourceChatRoomId,
                quizSetId = quizSetId,
                weekStartedOn = week.startedOn,
                memberId1 = minOf(memberIdA, memberIdB),
                memberId2 = maxOf(memberIdA, memberIdB),
            )
        }
    }
}
