package com.ditto.domain.member.entity

import com.ditto.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.Comment
import java.time.LocalDateTime

/**
 * 닉네임 중복 확인을 통과한 회원이 [HOLD_MINUTES]분 동안 그 닉네임을 붙잡아 두는 예약.
 *
 * 가입 중인 두 사람이 같은 닉네임을 고르면, 먼저 확인한 쪽이 가입을 끝낼 때까지 다른 쪽은 확인 단계에서
 * "사용 중"을 받는다. 예약은 선점일 뿐 저장이 아니다 — 최종 방어선은 `member.nickname` 유니크 제약이다.
 *
 * 회원당 하나다([memberId] 유니크). 만료된 예약은 지우지 않고 다음 예약이 덮어쓴다 — 청소 배치가 필요 없다.
 */
@Entity
@Table(
    name = "nickname_reservation",
    uniqueConstraints = [
        UniqueConstraint(name = "nickname_reservation_uk_1", columnNames = ["nickname"]),
        UniqueConstraint(name = "nickname_reservation_uk_2", columnNames = ["member_id"]),
    ],
)
class NicknameReservation private constructor(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("예약한 닉네임")
    @Column(nullable = false, length = 50)
    var nickname: String,

    @Comment("예약한 회원 ID")
    @Column(name = "member_id", nullable = false)
    val memberId: Long,

    @Comment("예약 만료 시각")
    @Column(name = "expires_at", nullable = false)
    var expiresAt: LocalDateTime,
) : BaseEntity() {

    /** [memberId]가 아닌 다른 회원이 [now] 시점에 이 예약을 쥐고 있는가. */
    fun isHeldByOtherThan(memberId: Long, now: LocalDateTime): Boolean =
        this.memberId != memberId && now < expiresAt

    /** 같은 회원이 새 닉네임을 확인했거나 같은 닉네임을 다시 확인했다 — 예약을 옮기고 만료를 늘린다. */
    fun renew(nickname: String, now: LocalDateTime) {
        this.nickname = nickname
        this.expiresAt = now.plusMinutes(HOLD_MINUTES)
    }

    companion object {
        const val HOLD_MINUTES = 10L

        fun reserve(memberId: Long, nickname: String, now: LocalDateTime): NicknameReservation =
            NicknameReservation(
                nickname = nickname,
                memberId = memberId,
                expiresAt = now.plusMinutes(HOLD_MINUTES),
            )
    }
}
