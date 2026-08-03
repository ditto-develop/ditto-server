package com.ditto.domain.member.entity

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

/**
 * 사용자 간 차단. 차단 화면(피그마 6.2.2)의 문구가 곧 이 관계의 효력이다 —
 * "차단한 사용자는 나의 프로필을 볼 수 없고, 매칭에서 제외돼요. 언제든지 해제할 수 있어요."
 *
 * **제재(`sanction`)와 다른 물건이다.** 제재는 운영자가 계정 전체에 내리는 조치이고 해제도 운영자만 한다.
 * 차단은 [blockerId]와 [blockedMemberId] 쌍에만 효력이 있으며 당사자가 즉시 만들고 즉시 해제한다.
 *
 * 생성 경로는 신고 화면의 선택 체크박스("이 사용자 차단하기")와 직접 차단 API 두 가지다.
 * 신고 여부와 무관하게 독립적으로 존재하며, 신고가 기각돼도 차단은 남는다(내 차단은 내 의사).
 *
 * 차단 사유는 받지 않는다 — 화면이 사유를 묻지도, 노출하지도 않는다.
 */
@Entity
@Table(
    name = "member_block",
    uniqueConstraints = [
        UniqueConstraint(name = "member_block_uk_1", columnNames = ["blocker_id", "blocked_member_id"]),
    ],
    indexes = [
        Index(name = "member_block_index_1", columnList = "blocker_id, created_at"),
        Index(name = "member_block_index_2", columnList = "blocked_member_id, blocker_id"),
    ],
)
class MemberBlock private constructor(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("차단한 회원 ID")
    @Column(name = "blocker_id", nullable = false)
    val blockerId: Long,

    @Comment("차단된 회원 ID")
    @Column(name = "blocked_member_id", nullable = false)
    val blockedMemberId: Long,
) : BaseEntity() {

    companion object {
        fun create(blockerId: Long, blockedMemberId: Long): MemberBlock = MemberBlock(
            blockerId = blockerId,
            blockedMemberId = blockedMemberId,
        )
    }
}
