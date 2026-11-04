package com.ditto.domain.quiz.entity

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

/** 마감 스냅샷의 완주자별 결과. 어드민은 모집단에서 빠지므로 행이 없다. */
@Entity
@Table(
    name = "quiz_member_stat",
    uniqueConstraints = [
        UniqueConstraint(name = "quiz_member_stat_uk_1", columnNames = ["quiz_set_id", "member_id"]),
    ],
    indexes = [
        Index(name = "quiz_member_stat_index_1", columnList = "member_id"),
    ],
)
class QuizMemberStat private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("퀴즈 세트 ID")
    @Column(name = "quiz_set_id", nullable = false)
    val quizSetId: Long,

    @Comment("회원 ID")
    @Column(name = "member_id", nullable = false)
    val memberId: Long,

    @Comment("내 답이 소수 의견이었던 문항 수")
    @Column(name = "minority_count", nullable = false)
    val minorityCount: Int,

    @Comment("같은 셋 완주자 중 나와 같은 답이 overlap_threshold 개 이상인 사람 수 (본인 제외)")
    @Column(name = "overlap_count", nullable = false)
    val overlapCount: Int,
) : BaseEntity() {

    companion object {
        fun of(quizSetId: Long, memberId: Long, minorityCount: Int, overlapCount: Int): QuizMemberStat =
            QuizMemberStat(
                quizSetId = quizSetId,
                memberId = memberId,
                minorityCount = minorityCount,
                overlapCount = overlapCount,
            )
    }
}
