package com.ditto.domain.quiz.entity

import com.ditto.domain.BaseEntity
import com.ditto.domain.member.entity.GenderPreference
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
    // 별도 선택 전 기본값은 이성(OPPOSITE) — 연애 매칭의 기본 동작.
    preferredGender: GenderPreference = GenderPreference.OPPOSITE,
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

    @Comment("매칭 성별 선호 (OPPOSITE, SAME, ANY). 기본값 OPPOSITE")
    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_gender", nullable = false, length = 20)
    var preferredGender: GenderPreference = preferredGender
        protected set

    fun recordAnswer() {
        answeredCount++
        if (answeredCount >= totalCount) {
            status = QuizProgressStatus.COMPLETED
            return
        }
        status = QuizProgressStatus.IN_PROGRESS
    }

    /** 이 퀴즈에서의 매칭 성별 선호를 설정한다. (연애 퀴즈 참여 시 선택) */
    fun selectPreferredGender(preferredGender: GenderPreference) {
        this.preferredGender = preferredGender
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
