package com.ditto.domain.review.entity

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.BaseEntity
import com.ditto.domain.chat.entity.ChatRoomType
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
import java.time.LocalDate
import java.time.LocalDateTime
import org.hibernate.annotations.Comment

/**
 * 한 회원이 한 채팅 종료 건에 대해 진행하는 평가. 안에 평가 대상 수만큼 [ReviewAnswer]를 가진다.
 *
 * [matchType]·[matchId]는 `chat_room`의 `(source_type, source_id)`와 같은 값이다. 조회 때마다 채팅방을
 * 조인하지 않으려고 복사해 두며, 양쪽 다 생성 후 불변이라 어긋나지 않는다.
 * 타입으로 [ChatRoomType]을 재사용하는 이유는 값·의미가 동일해 별도 enum을 두면 동기화 비용만 생기기 때문이다.
 */
@Entity
@Table(
    name = "member_review",
    uniqueConstraints = [
        // 동일 종료 이벤트를 다시 처리해도 회원별 진행 단위는 하나만 존재한다.
        UniqueConstraint(
            name = "member_review_uk_1",
            columnNames = ["chat_room_id", "author_member_id"],
        ),
    ],
    indexes = [
        Index(name = "member_review_index_1", columnList = "author_member_id, status, available_at"),
    ],
)
class MemberReview private constructor(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("리뷰 작성자 회원 ID")
    @Column(name = "author_member_id", nullable = false)
    val authorMemberId: Long,

    @Comment("매칭 유형 (PERSONAL, GROUP)")
    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false, length = 20)
    val matchType: ChatRoomType,

    @Comment("매칭 ID (personal_match 또는 group_match 의 ID)")
    @Column(name = "match_id", nullable = false)
    val matchId: Long,

    @Comment("평가 문맥이 된 채팅방 ID")
    @Column(name = "chat_room_id", nullable = false)
    val chatRoomId: Long,

    @Comment("원본 퀴즈셋 ID")
    @Column(name = "quiz_set_id", nullable = false)
    val quizSetId: Long,

    @Comment("운영 주 시작일 (해당 주 월요일)")
    @Column(name = "week_started_on", nullable = false)
    val weekStartedOn: LocalDate,

    @Comment("평가 가능 시각 (채팅 종료 시각)")
    @Column(name = "available_at", nullable = false)
    val availableAt: LocalDateTime,
) : BaseEntity() {

    @Comment("진행 상태")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ReviewProgressStatus = ReviewProgressStatus.NOT_STARTED
        protected set

    @Comment("전체 완료 시각 (미완료면 NULL)")
    @Column(name = "completed_at")
    var completedAt: LocalDateTime? = null
        protected set

    fun isAuthor(memberId: Long): Boolean = authorMemberId == memberId

    /** 재매칭 의사를 받는 평가인지. 재매칭은 그룹에서 만난 사람과만 신청할 수 있다. */
    fun canRematch(): Boolean = matchType == ChatRoomType.GROUP

    /**
     * 이 평가에 재매칭 의사를 실을 수 있는지 확인한다 — 받지 않는 평가(1:1)에 값이 오면 거부한다.
     * 반대 방향(재매칭을 받는데 의사가 없음)은 값을 실제로 쓰는 `RematchSubmitter`가 검증한다.
     */
    fun validateRematchAnswerAllowed(wantsOneToOneRematch: Boolean?) {
        if (!canRematch() && wantsOneToOneRematch != null) {
            throw WarnException(ErrorCode.INVALID_REVIEW_ANSWER, "1:1 평가에는 재매칭 의사를 보낼 수 없습니다.")
        }
    }

    /**
     * 대상 하나가 확정될 때마다 진행 상태를 갱신한다.
     * 마지막 미응답 대상이 확정되면 별도 완료 요청 없이 [ReviewProgressStatus.COMPLETED]로 전이한다.
     *
     * 완료된 평가는 더 갱신하지 않는다 — 자식([ReviewAnswer])의 재제출 거부는 다른 행에 있는 가드라
     * 부모를 보호하지 못하므로, `completedAt != null` ⟺ `COMPLETED` 불변식을 여기서 직접 지킨다.
     */
    fun recordAnswer(hasRemainingTarget: Boolean, answeredAt: LocalDateTime) {
        if (status == ReviewProgressStatus.COMPLETED) {
            throw WarnException(ErrorCode.REVIEW_ALREADY_ANSWERED, "이미 완료된 평가입니다.")
        }
        if (hasRemainingTarget) {
            status = ReviewProgressStatus.IN_PROGRESS
            return
        }
        status = ReviewProgressStatus.COMPLETED
        completedAt = answeredAt
    }

    companion object {
        fun create(
            authorMemberId: Long,
            matchType: ChatRoomType,
            matchId: Long,
            chatRoomId: Long,
            quizSetId: Long,
            weekStartedOn: LocalDate,
            availableAt: LocalDateTime,
        ): MemberReview = MemberReview(
            authorMemberId = authorMemberId,
            matchType = matchType,
            matchId = matchId,
            chatRoomId = chatRoomId,
            quizSetId = quizSetId,
            weekStartedOn = weekStartedOn,
            availableAt = availableAt,
        )
    }
}
