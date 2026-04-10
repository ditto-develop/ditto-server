package com.ditto.domain.quiz.entity

import com.ditto.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.Comment

@Entity
@Table(
    name = "quiz_choice",
    indexes = [
        Index(name = "quiz_choice_index_1", columnList = "quiz_id, display_order"),
    ],
)
class QuizChoice private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,
    @Comment("퀴즈 ID")
    @Column(name = "quiz_id", nullable = false)
    val quizId: Long,
    content: String,
    displayOrder: Int,
) : BaseEntity() {

    @Comment("선택지 내용")
    @Column(nullable = false, length = 200)
    var content: String = content
        protected set

    @Comment("선택지 노출 순서(1부터 시작)")
    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = displayOrder
        protected set

    companion object {
        fun create(
            quizId: Long,
            content: String,
            displayOrder: Int,
        ): QuizChoice = QuizChoice(
            quizId = quizId,
            content = content,
            displayOrder = displayOrder,
        )
    }
}
