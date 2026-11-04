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

/** 마감 스냅샷의 선택지별 집계. [percent]는 문항 안에서 합이 100이 되도록 보정한 값이다. */
@Entity
@Table(
    name = "quiz_choice_stat",
    uniqueConstraints = [
        UniqueConstraint(name = "quiz_choice_stat_uk_1", columnNames = ["quiz_set_id", "choice_id"]),
    ],
    indexes = [
        Index(name = "quiz_choice_stat_index_1", columnList = "quiz_id"),
    ],
)
class QuizChoiceStat private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("퀴즈 세트 ID")
    @Column(name = "quiz_set_id", nullable = false)
    val quizSetId: Long,

    @Comment("퀴즈 ID")
    @Column(name = "quiz_id", nullable = false)
    val quizId: Long,

    @Comment("선택지 ID")
    @Column(name = "choice_id", nullable = false)
    val choiceId: Long,

    @Comment("이 선택지를 고른 완주자 수")
    @Column(name = "select_count", nullable = false)
    val selectCount: Int,

    @Comment("정수 % (문항 안 합 100 보정 후)")
    @Column(nullable = false)
    val percent: Int,
) : BaseEntity() {

    companion object {
        fun of(quizSetId: Long, quizId: Long, choiceId: Long, selectCount: Int, percent: Int): QuizChoiceStat =
            QuizChoiceStat(
                quizSetId = quizSetId,
                quizId = quizId,
                choiceId = choiceId,
                selectCount = selectCount,
                percent = percent,
            )
    }
}
