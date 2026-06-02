package com.ditto.api.auth.controller

import com.ditto.api.auth.dto.TokenRefreshResponse
import com.ditto.api.auth.service.AuthService
import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.api.config.auth.RefreshTokenCookieFactory
import com.ditto.common.response.ApiResponse
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class AuthController(
    private val authService: AuthService,
    private val refreshTokenCookieFactory: RefreshTokenCookieFactory,
) {

    @PostMapping("/api/v1/users/auth/refresh")
    fun refresh(
        @CookieValue(RefreshTokenCookieFactory.REFRESH_TOKEN_COOKIE) refreshToken: String,
        response: HttpServletResponse,
    ): ApiResponse<TokenRefreshResponse> {
        val result = authService.refresh(refreshToken)
        refreshTokenCookieFactory.addTo(response, result.refreshToken)

        return ApiResponse.ok(TokenRefreshResponse(result.accessToken))
    }

    @PostMapping("/api/v1/users/auth/logout")
    fun logout(
        @AuthenticationPrincipal principal: MemberPrincipal,
        response: HttpServletResponse,
    ): ApiResponse<Unit> {
        authService.logout(principal.memberId)
        refreshTokenCookieFactory.expireTo(response)

        return ApiResponse(success = true)
    }
}
