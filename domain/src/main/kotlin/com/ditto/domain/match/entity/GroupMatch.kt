package com.ditto.domain.match.entity

import com.ditto.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.Comment

private const val ACTIVATION_THRESHOLD = 3

@Entity
@Table(
    name = "group_match",
    // quizSetId UK 제거 — 퀴즈셋 1개에서 여러 그룹 생성 가능
    // 멤버당 1개 참여 보장은 GroupMatchParticipant.(quiz_set_id, member_id) UK로 처리
    indexes = [
        Index(name = "group_match_index_1", columnList = "quiz_set_id, is_active"),
    ],
)
class GroupMatch private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("퀴즈 세트 ID")
    @Column(name = "quiz_set_id", nullable = false)
    val quizSetId: Long,

    score: Double = 0.0,
    isActive: Boolean = false,
    participantCount: Int = 0,
) : BaseEntity() {

    @Comment("그룹 점수 (구성원 모든 페어 일치율의 평균, 0.0~100.0)")
    @Column(nullable = false)
    var score: Double = score
        protected set

    @Comment("활성화 여부 (참가자 3명 이상)")
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = isActive
        protected set

    @Comment("수락자 수")
    @Column(name = "participant_count", nullable = false)
    var participantCount: Int = participantCount
        protected set

    /**
     * 구성원 한 명의 수락을 기록한다. 수락자가 [ACTIVATION_THRESHOLD]명에 닿으면 그룹이 성사되고,
     * 성사된 방은 금요일에 채팅방이 열린다. 되돌릴 수 없다 — 화면에서도 "취소할 수 없어요"로 안내한다.
     */
    fun recordAcceptance() {
        participantCount++
        if (participantCount >= ACTIVATION_THRESHOLD) {
            isActive = true
        }
    }

    companion object {
        /**
         * 배치가 미리 짜는 후보 그룹. 아직 아무도 수락하지 않았으므로 비활성·수락자 0으로 시작한다.
         * [score]는 구성원 모든 페어 일치율의 평균이다.
         */
        fun candidate(quizSetId: Long, score: Double): GroupMatch =
            GroupMatch(quizSetId = quizSetId, score = score)
    }
}
