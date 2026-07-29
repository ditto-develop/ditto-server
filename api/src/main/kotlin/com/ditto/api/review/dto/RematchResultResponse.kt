package com.ditto.api.review.dto

import java.time.LocalDateTime

/** 상호 성사된 재매칭. 채팅방은 후속 트랙(`I2`)이 열므로 여기에는 방 정보가 없다. */
data class RematchResultResponse(
    val matchedMemberId: Long,
    val matchedAt: LocalDateTime,
)
