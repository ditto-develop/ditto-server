package com.ditto.domain.match.entity

import com.ditto.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.Comment

@Entity
@Table(
    name = "group_match_decline",
    uniqueConstraints = [
        // 한 멤버는 동일 퀴즈셋에서 한 번만 거절 가능
        UniqueConstraint(
            name = "group_match_decline_uk_1",
            columnNames = ["quiz_set_id", "member_id"],
        ),
    ],
)
class GroupMatchDecline private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("퀴즈 세트 ID")
    @Column(name = "quiz_set_id", nullable = false)
    val quizSetId: Long,

    @Comment("회원 ID")
    @Column(name = "member_id", nullable = false)
    val memberId: Long,
) : BaseEntity() {

    companion object {
        fun of(quizSetId: Long, memberId: Long): GroupMatchDecline =
            GroupMatchDecline(quizSetId = quizSetId, memberId = memberId)
    }
}
