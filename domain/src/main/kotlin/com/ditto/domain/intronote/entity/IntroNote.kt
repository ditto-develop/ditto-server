package com.ditto.domain.intronote.entity

import com.ditto.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.Comment

@Entity
@Table(
    name = "intro_note",
    uniqueConstraints = [
        UniqueConstraint(name = "intro_note_uk_1", columnNames = ["member_id", "question"]),
    ],
    indexes = [
        Index(name = "intro_note_index_1", columnList = "member_id"),
    ],
)
class IntroNote private constructor(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("회원 ID")
    @Column(name = "member_id", nullable = false)
    val memberId: Long,

    @Comment("소개노트 질문")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    val question: IntroQuestion,

    @Comment("답변 (빈 문자열 허용 — 부분 저장)")
    @Column(nullable = false, length = ANSWER_MAX_LENGTH)
    var answer: String,
) : BaseEntity() {

    fun updateAnswer(answer: String) {
        this.answer = answer
    }

    companion object {
        const val ANSWER_MAX_LENGTH = 500

        fun create(
            memberId: Long,
            question: IntroQuestion,
            answer: String,
        ): IntroNote = IntroNote(
            memberId = memberId,
            question = question,
            answer = answer,
        )
    }
}
