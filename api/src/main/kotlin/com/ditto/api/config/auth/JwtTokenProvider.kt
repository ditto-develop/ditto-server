package com.ditto.api.config.auth

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    private val jwtProperties: JwtProperties,
) {
    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray())
    }

    private val parser by lazy {
        Jwts.parser().verifyWith(key).build()
    }

    fun generateAccessToken(memberId: Long, now: Date = Date()): String {
        return Jwts.builder()
            .subject(memberId.toString())
            .issuedAt(now)
            .expiration(Date(now.time + jwtProperties.expirationMs))
            .signWith(key)
            .compact()
    }

    fun getMemberId(token: String): Long =
        parser.parseSignedClaims(token).payload.subject.toLong()

    fun isValid(token: String): Boolean =
        try {
            parser.parseSignedClaims(token)
            true
        } catch (e: Exception) {
            false
        }

    fun generateRefreshToken(): String = UUID.randomUUID().toString()

    fun createRefreshTokenExpiresAt(now: LocalDateTime = LocalDateTime.now()): LocalDateTime =
        now.plusSeconds(jwtProperties.refreshExpirationMs / 1_000)
}
