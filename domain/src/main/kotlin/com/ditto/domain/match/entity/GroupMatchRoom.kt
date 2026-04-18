package com.ditto.domain.match.entity

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

private const val ACTIVATION_THRESHOLD = 3

@Entity
@Table(
    name = "group_match_room",
    uniqueConstraints = [
        UniqueConstraint(name = "group_match_room_uk_1", columnNames = ["quiz_set_id"]),
    ],
    indexes = [
        Index(name = "group_match_room_index_1", columnList = "quiz_set_id, is_active"),
    ],
)
class GroupMatchRoom private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("퀴즈 세트 ID")
    @Column(name = "quiz_set_id", nullable = false, unique = true)
    val quizSetId: Long,

    isActive: Boolean = false,
    participantCount: Int = 0,
) : BaseEntity() {

    @Comment("활성화 여부 (참가자 3명 이상)")
    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = isActive
        protected set

    @Comment("참가자 수")
    @Column(name = "participant_count", nullable = false)
    var participantCount: Int = participantCount
        protected set

    fun addParticipant() {
        participantCount++
        if (participantCount >= ACTIVATION_THRESHOLD) {
            isActive = true
        }
    }

    companion object {
        fun create(quizSetId: Long): GroupMatchRoom = GroupMatchRoom(quizSetId = quizSetId)
    }
}
