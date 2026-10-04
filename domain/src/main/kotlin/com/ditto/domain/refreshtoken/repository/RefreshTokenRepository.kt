package com.ditto.domain.refreshtoken.repository

import com.ditto.domain.refreshtoken.entity.RefreshToken
import com.ditto.domain.refreshtoken.repository.querydsl.RefreshTokenRepositoryCustom
import org.springframework.data.jpa.repository.JpaRepository

interface RefreshTokenRepository : JpaRepository<RefreshToken, Long>, RefreshTokenRepositoryCustom {

    fun findByMemberIdIn(memberIds: Collection<Long>): List<RefreshToken>
    fun findByToken(token: String): RefreshToken?
}
