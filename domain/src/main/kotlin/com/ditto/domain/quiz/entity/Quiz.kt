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
    name = "quiz",
    indexes = [
        Index(name = "quiz_index_1", columnList = "quiz_set_id, display_order"),
    ],
)
class Quiz(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,
    @Comment("퀴즈 세트 ID")
    @Column(name = "quiz_set_id", nullable = false)
    val quizSetId: Long,
    question: String,
    displayOrder: Int,
) : BaseEntity() {

    @Comment("퀴즈 질문")
    @Column(nullable = false, length = 500)
    var question: String = question
        protected set

    @Comment("퀴즈 노출 순서")
    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = displayOrder
        protected set
}
