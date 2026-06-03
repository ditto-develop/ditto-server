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
) : BaseEntity() {

    companion object {
        fun create(
            ownerMemberId: Long,
            otherMemberId: Long,
            quizSetId: Long,
            score: Double,
        ): MatchCandidate = MatchCandidate(
            ownerMemberId = ownerMemberId,
            otherMemberId = otherMemberId,
            quizSetId = quizSetId,
            score = score,
        )
    }
}
