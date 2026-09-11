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
    name = "group_match_member",
    uniqueConstraints = [
        // 같은 방에 같은 멤버가 중복 입장 불가
        // 단, 한 멤버가 동일 퀴즈셋의 여러 방에 참여하는 것은 허용
        UniqueConstraint(
            name = "group_match_member_uk_1",
            columnNames = ["room_id", "member_id"],
        ),
    ],
    indexes = [
        Index(name = "group_match_member_index_1", columnList = "room_id"),
        Index(name = "group_match_member_index_2", columnList = "member_id"),
        Index(name = "group_match_member_index_3", columnList = "member_id, status"),
    ],
)
class GroupMatchMember private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("그룹 매칭 방 ID")
    @Column(name = "room_id", nullable = false)
    val roomId: Long,

    @Comment("회원 ID")
    @Column(name = "member_id", nullable = false)
    val memberId: Long,

    status: InvitationStatus,
) : BaseEntity() {

    @Comment("후보 응답 상태")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: InvitationStatus = status
        protected set

    /** 초대를 수락한다. 되돌릴 수 없다 — 화면에서도 "취소할 수 없어요"로 안내한다. */
    fun accept() {
        status = InvitationStatus.ACCEPTED
    }

    /** 초대를 거절한다. 본인이 누른 거절과 다른 그룹 수락에 따른 자동 거절이 같은 상태를 쓴다. */
    fun decline() {
        status = InvitationStatus.DECLINED
    }

    fun isPending(): Boolean = status == InvitationStatus.PENDING

    companion object {
        /** 배치가 후보 그룹에 앉히는 멤버. 본인이 응답하기 전까지 대기 상태다. */
        fun candidate(roomId: Long, memberId: Long): GroupMatchMember =
            GroupMatchMember(
                roomId = roomId,
                memberId = memberId,
                status = InvitationStatus.PENDING,
            )
    }
}
