package com.ditto.domain.quiz.entity

import com.ditto.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.Comment

/**
 * 마감된 퀴즈셋의 통계 스냅샷 머리 행. 퀴즈셋당 하나이고, 있으면 그 셋의 [QuizChoiceStat]·[QuizMemberStat]도 다 있다
 * (한 트랜잭션에 같이 저장한다).
 *
 * 마감 시점 완주자(어드민 제외)가 모집단이다. 마감 뒤에는 답이 바뀌지 않으므로 다시 계산하지 않는다 —
 * 화면마다 다시 세면 반올림·모집단이 화면마다 갈린다.
 */
@Entity
@Table(
    name = "quiz_set_stat",
    uniqueConstraints = [
        UniqueConstraint(name = "quiz_set_stat_uk_1", columnNames = ["quiz_set_id"]),
    ],
)
class QuizSetStat private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("퀴즈 세트 ID")
    @Column(name = "quiz_set_id", nullable = false)
    val quizSetId: Long,

    @Comment("마감 시점 완주자 수")
    @Column(name = "participant_count", nullable = false)
    val participantCount: Int,

    @Comment("'나와 답이 겹친 사람' 기준 문항 수 = ceil(문항 수 × 2/3), 스냅샷 시점 문항 수로 고정")
    @Column(name = "overlap_threshold", nullable = false)
    val overlapThreshold: Int,
) : BaseEntity() {

    companion object {
        fun of(quizSetId: Long, participantCount: Int, overlapThreshold: Int): QuizSetStat =
            QuizSetStat(quizSetId = quizSetId, participantCount = participantCount, overlapThreshold = overlapThreshold)
    }
}
