package com.ditto.api.config.auth

import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component

/**
 * refreshToken을 담는 HttpOnly 쿠키를 응답에 설정한다.
 * - HttpOnly: JS(XSS)의 토큰 탈취 차단
 * - Secure/SameSite/Path: [CookieProperties]로 환경별 제어 (로컬 http는 secure=false)
 * - Max-Age: refresh 토큰 만료(ms)와 동일
 */
@Component
class RefreshTokenCookieFactory(
    private val cookieProperties: CookieProperties,
    private val jwtProperties: JwtProperties,
) {
    fun addTo(
        response: HttpServletResponse,
        token: String,
    ) = response.addHeader(
        HttpHeaders.SET_COOKIE,
        createCookie(token, jwtProperties.refreshExpirationMs / 1_000).toString()
    )

    fun expireTo(response: HttpServletResponse)
    = response.addHeader(
        HttpHeaders.SET_COOKIE,
        createCookie("", 0).toString()
    )

    private fun createCookie(
        value: String,
        maxAgeSeconds: Long,
    ): ResponseCookie =
        ResponseCookie.from(REFRESH_TOKEN_COOKIE, value)
            .httpOnly(true)
            .secure(cookieProperties.secure)
            .sameSite(cookieProperties.sameSite)
            .path(cookieProperties.path)
            .maxAge(maxAgeSeconds)
            .build()

    companion object {
        const val REFRESH_TOKEN_COOKIE = "refreshToken"
    }
}
