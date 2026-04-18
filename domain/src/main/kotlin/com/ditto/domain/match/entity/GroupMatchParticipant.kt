package com.ditto.domain.match.entity

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
    name = "group_match_participant",
    uniqueConstraints = [
        UniqueConstraint(
            name = "group_match_participant_uk_1",
            columnNames = ["room_id", "member_id"],
        ),
    ],
    indexes = [
        Index(name = "group_match_participant_index_1", columnList = "member_id, room_id"),
    ],
)
class GroupMatchParticipant private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("그룹 매칭 방 ID")
    @Column(name = "room_id", nullable = false)
    val roomId: Long,

    @Comment("회원 ID")
    @Column(name = "member_id", nullable = false)
    val memberId: Long,

    @Comment("참여 상태")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val status: GroupMatchParticipantStatus,
) : BaseEntity() {

    companion object {
        fun join(roomId: Long, memberId: Long): GroupMatchParticipant = GroupMatchParticipant(
            roomId = roomId,
            memberId = memberId,
            status = GroupMatchParticipantStatus.JOINED,
        )

        fun decline(roomId: Long, memberId: Long): GroupMatchParticipant = GroupMatchParticipant(
            roomId = roomId,
            memberId = memberId,
            status = GroupMatchParticipantStatus.DECLINED,
        )
    }
}
