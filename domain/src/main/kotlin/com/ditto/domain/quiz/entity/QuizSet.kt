package com.ditto.domain.quiz.entity

import com.ditto.domain.BaseEntity
import com.ditto.domain.system.OperationWeek
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.Comment
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(
    name = "quiz_set",
    indexes = [
        Index(name = "quiz_set_index_1", columnList = "week_started_on"),
        Index(name = "quiz_set_index_2", columnList = "start_date, end_date, is_active"),
    ],
)
class QuizSet private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,
    weekStartedOn: LocalDate,
    category: String,
    title: String,
    description: String? = null,
    startDate: LocalDateTime,
    endDate: LocalDateTime,
    isActive: Boolean = false,
    matchingType: MatchingType = MatchingType.ONE_TO_ONE,
) : BaseEntity() {

    @Comment("운영 주 시작일 (해당 주 월요일)")
    @Column(name = "week_started_on", nullable = false)
    var weekStartedOn: LocalDate = weekStartedOn
        protected set

    /** 이 퀴즈셋이 속한 운영 주. */
    val operationWeek: OperationWeek
        get() = OperationWeek(weekStartedOn)

    @Comment("카테고리")
    @Column(nullable = false, length = 50)
    var category: String = category
        protected set

    @Comment("퀴즈 세트 제목")
    @Column(nullable = false, length = 100)
    var title: String = title
        protected set

    @Comment("퀴즈 세트 설명")
    @Column(nullable = true, length = 500)
    var description: String? = description
        protected set

    @Comment("시작일시")
    @Column(name = "start_date", nullable = false)
    var startDate: LocalDateTime = startDate
        protected set

    @Comment("종료일시")
    @Column(name = "end_date", nullable = false)
    var endDate: LocalDateTime = endDate
        protected set

    @Comment("활성화 여부")
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = isActive
        protected set

    @Comment("매칭 타입")
    @Enumerated(EnumType.STRING)
    @Column(name = "matching_type", nullable = false, length = 20)
    var matchingType: MatchingType = matchingType
        protected set

    fun activate() {
        isActive = true
    }

    fun deactivate() {
        isActive = false
    }

    /** 퀴즈셋 메타 정보를 수정한다. 주간 식별자(weekStartedOn)는 변경된 startDate가 속한 주로 재파생된다. */
    fun update(
        category: String,
        title: String,
        description: String?,
        startDate: LocalDateTime,
        endDate: LocalDateTime,
        matchingType: MatchingType,
    ) {
        this.category = category
        this.title = title
        this.description = description
        this.startDate = startDate
        this.endDate = endDate
        this.weekStartedOn = OperationWeek.containing(startDate.toLocalDate()).startedOn
        this.matchingType = matchingType
    }

    companion object {
        fun create(
            category: String,
            title: String,
            description: String? = null,
            startDate: LocalDateTime,
            endDate: LocalDateTime,
            isActive: Boolean = false,
            matchingType: MatchingType = MatchingType.ONE_TO_ONE,
        ): QuizSet = QuizSet(
            weekStartedOn = OperationWeek.containing(startDate.toLocalDate()).startedOn,
            category = category,
            title = title,
            description = description,
            startDate = startDate,
            endDate = endDate,
            isActive = isActive,
            matchingType = matchingType,
        )
    }
}
