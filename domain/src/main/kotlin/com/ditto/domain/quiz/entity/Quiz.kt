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
class Quiz private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,
    @Comment("퀴즈 세트 ID")
    @Column(name = "quiz_set_id", nullable = false)
    val quizSetId: Long,
    question: String,
    displayOrder: Int,
    hintQuestion: String? = null,
) : BaseEntity() {

    @Comment("퀴즈 질문")
    @Column(nullable = false, length = 500)
    var question: String = question
        protected set

    @Comment("퀴즈 노출 순서")
    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = displayOrder
        protected set

    /**
     * 대화방에서 이 문항으로 대화를 잇게 하는 질문(어드민 입력, 선택). 첫 대화 힌트·질문 카드에 실린다.
     * 비어 있으면 FE 가 공통 문구를 쓴다.
     */
    @Comment("대화 힌트 질문 (어드민 입력, 선택)")
    @Column(name = "hint_question", nullable = true, length = HINT_QUESTION_MAX_LENGTH)
    var hintQuestion: String? = hintQuestion
        protected set

    fun update(question: String, displayOrder: Int, hintQuestion: String? = this.hintQuestion) {
        this.question = question
        this.displayOrder = displayOrder
        this.hintQuestion = hintQuestion
    }

    companion object {
        const val HINT_QUESTION_MAX_LENGTH = 200

        fun create(
            quizSetId: Long,
            question: String,
            displayOrder: Int,
            hintQuestion: String? = null,
        ): Quiz = Quiz(
            quizSetId = quizSetId,
            question = question,
            displayOrder = displayOrder,
            hintQuestion = hintQuestion,
        )
    }
}
