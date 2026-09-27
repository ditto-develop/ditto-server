package com.ditto.domain.member.repository

import com.ditto.domain.member.entity.NicknameReservation
import org.springframework.data.jpa.repository.JpaRepository

interface NicknameReservationRepository : JpaRepository<NicknameReservation, Long> {

    /** DB 콜레이션으로 비교한다 — `member.nickname` 유니크와 같은 규칙이어야 예약과 저장이 어긋나지 않는다. */
    fun findByNickname(nickname: String): NicknameReservation?

    fun findByMemberId(memberId: Long): NicknameReservation?
}
