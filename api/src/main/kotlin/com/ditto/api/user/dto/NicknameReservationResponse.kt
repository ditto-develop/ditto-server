package com.ditto.api.user.dto

import java.time.LocalDateTime

/** 닉네임 확인 v2 응답. v1([CheckNicknameResponse])에 예약 만료 시각을 더했다. */
data class NicknameReservationResponse(
    val available: Boolean,
    /** 사용 가능하면 내 예약이 풀리는 시각. 사용 불가면 null. */
    val reservedUntil: LocalDateTime?,
)
