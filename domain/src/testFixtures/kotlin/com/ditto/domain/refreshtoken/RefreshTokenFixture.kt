package com.ditto.domain.refreshtoken

import com.ditto.domain.refreshtoken.entity.RefreshToken
import com.ditto.domain.withId
import java.time.LocalDateTime
import java.util.UUID

object RefreshTokenFixture {

    fun create(
        memberId: Long = 1L,
        token: String = UUID.randomUUID().toString(),
        expiresAt: LocalDateTime = LocalDateTime.now().plusDays(14),
        id: Long = 0L,
    ): RefreshToken = RefreshToken.create(
        memberId = memberId,
        token = token,
        expiresAt = expiresAt,
    ).withId(id)
}
