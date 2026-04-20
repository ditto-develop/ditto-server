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
        // 한 멤버는 동일 퀴즈셋에서 하나의 그룹에만 참여 가능
        // (room_id, member_id) 가 아닌 (quiz_set_id, member_id) 로 유니크 보장
        UniqueConstraint(
            name = "group_match_participant_uk_1",
            columnNames = ["quiz_set_id", "member_id"],
        ),
    ],
    indexes = [
        Index(name = "group_match_participant_index_1", columnList = "quiz_set_id, member_id"),
        Index(name = "group_match_participant_index_2", columnList = "room_id"),
    ],
)
class GroupMatchParticipant private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("퀴즈 세트 ID")
    @Column(name = "quiz_set_id", nullable = false)
    val quizSetId: Long,

    @Comment("회원 ID")
    @Column(name = "member_id", nullable = false)
    val memberId: Long,

    @Comment("참여 상태")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val status: GroupMatchParticipantStatus,

    @Comment("그룹 매칭 방 ID (JOINED인 경우에만 존재)")
    @Column(name = "room_id", nullable = true)
    val roomId: Long? = null,
) : BaseEntity() {

    companion object {
        fun join(quizSetId: Long, memberId: Long, roomId: Long): GroupMatchParticipant =
            GroupMatchParticipant(
                quizSetId = quizSetId,
                memberId = memberId,
                status = GroupMatchParticipantStatus.JOINED,
                roomId = roomId,
            )

        fun decline(quizSetId: Long, memberId: Long): GroupMatchParticipant =
            GroupMatchParticipant(
                quizSetId = quizSetId,
                memberId = memberId,
                status = GroupMatchParticipantStatus.DECLINED,
                roomId = null,
            )
    }
}
