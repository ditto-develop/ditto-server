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
import org.hibernate.annotations.Comment
import java.time.LocalDateTime

@Entity
@Table(
    name = "quiz_set",
    indexes = [
        Index(name = "quiz_set_index_1", columnList = "year_no, month_no, week_no"),
    ],
)
class QuizSet(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,
    @Comment("년도")
    @Column(name = "year_no", nullable = false)
    val year: Int,
    @Comment("월")
    @Column(name = "month_no", nullable = false)
    val month: Int,
    @Comment("주차")
    @Column(name = "week_no", nullable = false)
    val week: Int,
    category: String,
    title: String,
    description: String? = null,
    startDate: LocalDateTime,
    endDate: LocalDateTime,
    isActive: Boolean = false,
    matchingType: MatchingType = MatchingType.ONE_TO_ONE,
) : BaseEntity() {

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
}
