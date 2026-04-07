package com.ditto.domain.refreshtoken.repository

import com.ditto.domain.refreshtoken.entity.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional

interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {

    fun findByToken(token: String): RefreshToken?

    @Transactional
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.memberId = :memberId")
    fun deleteAllByMemberId(memberId: Long)
}
