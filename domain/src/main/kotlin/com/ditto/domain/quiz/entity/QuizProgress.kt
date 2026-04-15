package com.ditto.domain.quiz.entity

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
    name = "quiz_progress",
    uniqueConstraints = [
        UniqueConstraint(name = "quiz_progress_uk_1", columnNames = ["member_id", "quiz_set_id"]),
    ],
    indexes = [
        Index(name = "quiz_progress_index_1", columnList = "quiz_set_id, status"),
    ],
)
class QuizProgress private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,
    @Comment("회원 ID")
    @Column(name = "member_id", nullable = false)
    val memberId: Long,
    @Comment("퀴즈 세트 ID")
    @Column(name = "quiz_set_id", nullable = false)
    val quizSetId: Long,
    @Comment("전체 퀴즈 수")
    @Column(name = "total_count", nullable = false)
    val totalCount: Int,
    status: QuizProgressStatus = QuizProgressStatus.NOT_STARTED,
    answeredCount: Int = 0,
) : BaseEntity() {

    @Comment("진행 상태")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: QuizProgressStatus = status
        protected set

    @Comment("답변한 퀴즈 수")
    @Column(name = "answered_count", nullable = false)
    var answeredCount: Int = answeredCount
        protected set

    fun recordAnswer() {
        answeredCount++
        if (answeredCount >= totalCount) {
            status = QuizProgressStatus.COMPLETED
            return
        }
        status = QuizProgressStatus.IN_PROGRESS
    }

    companion object {
        fun create(
            memberId: Long,
            quizSetId: Long,
            totalCount: Int,
        ): QuizProgress = QuizProgress(
            memberId = memberId,
            quizSetId = quizSetId,
            totalCount = totalCount,
        )
    }
}
