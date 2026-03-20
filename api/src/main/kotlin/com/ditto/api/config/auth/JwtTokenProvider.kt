package com.ditto.api.config.auth

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Component
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtTokenProvider(
    private val jwtProperties: JwtProperties,
) {
    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray())
    }

    fun generateToken(memberId: Long): String {
        val now = Date()
        val expiration = Date(now.time + jwtProperties.expirationMs)

        return Jwts.builder()
            .subject(memberId.toString())
            .issuedAt(now)
            .expiration(expiration)
            .signWith(key)
            .compact()
    }

    fun getMemberId(token: String): Long {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload
            .subject
            .toLong()
    }

    fun isValid(token: String): Boolean {
        return try {
            Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
            true
        } catch (e: Exception) {
            false
        }
    }
}
