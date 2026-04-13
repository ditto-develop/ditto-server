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

@Entity
@Table(
    name = "quiz_answer",
    uniqueConstraints = [
        UniqueConstraint(name = "quiz_answer_uk_1", columnNames = ["member_id", "quiz_id"]),
    ],
    indexes = [
        Index(name = "quiz_answer_index_1", columnList = "quiz_id"),
        Index(name = "quiz_answer_index_2", columnList = "choice_id"),
    ],
)
class QuizAnswer private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,
    @Comment("회원 ID")
    @Column(name = "member_id", nullable = false)
    val memberId: Long,
    @Comment("퀴즈 ID")
    @Column(name = "quiz_id", nullable = false)
    val quizId: Long,
    choiceId: Long,
) : BaseEntity() {
    @Comment("선택지 ID")
    @Column(name = "choice_id", nullable = false)
    var choiceId: Long = choiceId
        protected set

    fun changeChoice(choiceId: Long) {
        this.choiceId = choiceId
    }

    companion object {
        fun create(
            memberId: Long,
            quizId: Long,
            choiceId: Long,
        ): QuizAnswer =
            QuizAnswer(
                memberId = memberId,
                quizId = quizId,
                choiceId = choiceId,
            )
    }
}
