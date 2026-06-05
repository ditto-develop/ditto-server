package com.ditto.domain.match.entity

import com.ditto.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.Comment

@Entity
@Table(
    name = "match_candidate",
    uniqueConstraints = [
        // 한 회원에게 동일 상대가 같은 퀴즈셋에서 중복 노출되지 않도록 (owner, other, quizSet) 유일
        UniqueConstraint(
            name = "match_candidate_uk_1",
            columnNames = ["owner_member_id", "other_member_id", "quiz_set_id"],
        ),
    ],
    indexes = [
        Index(name = "match_candidate_index_1", columnList = "owner_member_id, quiz_set_id, score"),
    ],
)
class MatchCandidate private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("후보를 노출받는 회원 ID (조회 주체)")
    @Column(name = "owner_member_id", nullable = false)
    val ownerMemberId: Long,

    @Comment("노출되는 상대 회원 ID")
    @Column(name = "other_member_id", nullable = false)
    val otherMemberId: Long,

    @Comment("퀴즈 세트 ID")
    @Column(name = "quiz_set_id", nullable = false)
    val quizSetId: Long,

    @Comment("매칭 점수 (0.0 ~ 100.0)")
    @Column(nullable = false)
    val score: Double,

    @Comment("일치한 문항 수")
    @Column(name = "matched_question_count", nullable = false)
    val matchedQuestionCount: Int,

    @Comment("전체 비교 문항 수")
    @Column(name = "total_question_count", nullable = false)
    val totalQuestionCount: Int,
) : BaseEntity() {

    companion object {
        const val MIN_SCORE = 0.0
        const val MAX_SCORE = 100.0

        fun create(
            ownerMemberId: Long,
            otherMemberId: Long,
            quizSetId: Long,
            score: Double,
            matchedQuestionCount: Int,
            totalQuestionCount: Int,
        ): MatchCandidate {
            // 배치가 생성하는 값이므로 위반 시 클라이언트가 아닌 알고리즘 버그다. 경계에서 불변식을 강제한다.
            require(ownerMemberId != otherMemberId) {
                "자기 자신은 매칭 후보가 될 수 없습니다: $ownerMemberId"
            }
            require(score in MIN_SCORE..MAX_SCORE) {
                "매칭 점수는 $MIN_SCORE~$MAX_SCORE 범위여야 합니다: $score"
            }
            require(totalQuestionCount >= 0) {
                "전체 문항 수는 음수일 수 없습니다: $totalQuestionCount"
            }
            require(matchedQuestionCount in 0..totalQuestionCount) {
                "일치 문항 수는 0~전체($totalQuestionCount) 범위여야 합니다: $matchedQuestionCount"
            }
            return MatchCandidate(
                ownerMemberId = ownerMemberId,
                otherMemberId = otherMemberId,
                quizSetId = quizSetId,
                score = score,
                matchedQuestionCount = matchedQuestionCount,
                totalQuestionCount = totalQuestionCount,
            )
        }
    }
}
