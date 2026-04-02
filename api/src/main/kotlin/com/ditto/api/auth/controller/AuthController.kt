package com.ditto.api.auth.controller

import com.ditto.api.auth.dto.TokenRefreshRequest
import com.ditto.api.auth.dto.TokenRefreshResponse
import com.ditto.api.auth.service.AuthService
import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.common.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class AuthController(
    private val authService: AuthService,
) {

    @PostMapping("/api/v1/users/auth/refresh")
    fun refresh(@Valid @RequestBody request: TokenRefreshRequest): ApiResponse<TokenRefreshResponse> {
        val result = authService.refresh(request)
        return ApiResponse.ok(result)
    }

    @PostMapping("/api/v1/users/auth/logout")
    fun logout(@AuthenticationPrincipal principal: MemberPrincipal): ApiResponse<Unit> {
        authService.logout(principal.provider, principal.providerUserId)
        return ApiResponse(success = true)
    }
}
