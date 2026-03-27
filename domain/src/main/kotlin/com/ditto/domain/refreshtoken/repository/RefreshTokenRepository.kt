package com.ditto.domain.refreshtoken.repository

import com.ditto.domain.refreshtoken.entity.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository

interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {

    fun findByToken(token: String): RefreshToken?

    fun deleteByMemberId(memberId: Long)
}
