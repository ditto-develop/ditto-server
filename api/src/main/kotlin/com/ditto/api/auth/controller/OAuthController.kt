package com.ditto.api.auth.controller

import com.ditto.api.auth.dto.NativeSocialLoginRequest
import com.ditto.api.auth.dto.NativeSocialLoginResponse
import com.ditto.api.auth.facade.OAuthFacade
import com.ditto.api.config.auth.RefreshTokenCookieFactory
import com.ditto.common.logging.Loggable
import com.ditto.common.response.ApiResponse
import com.ditto.domain.socialaccount.entity.SocialProvider
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
class OAuthController(
    private val oAuthFacade: OAuthFacade,
    private val refreshTokenCookieFactory: RefreshTokenCookieFactory,
) {

    @GetMapping("/api/v1/users/social-login/{provider}")
    fun login(
        @PathVariable provider: SocialProvider,
    ): ResponseEntity<Unit> {
        val authorizationUrl = oAuthFacade.getAuthorizationUrl(provider)
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(authorizationUrl))
            .build()
    }

    @GetMapping("/api/v1/users/social-login/{provider}/callback")
    fun callback(
        @PathVariable provider: SocialProvider,
        @RequestParam code: String,
        response: HttpServletResponse,
    ): ResponseEntity<Unit> {
        val oauthLoginResult = oAuthFacade.login(provider, code)
        oauthLoginResult.refreshToken?.let { refreshTokenCookieFactory.addTo(response, it) }

        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(oauthLoginResult.redirectUrl))
            .build()
    }

    /**
     * 네이티브 카카오 SDK가 받아온 액세스 토큰을 우리 토큰으로 교환한다(앱 전용).
     * 웹은 위 리다이렉트 경로를 그대로 쓴다 — 이 엔드포인트는 대체가 아니라 추가다.
     *
     * 제공자별 네이티브 SDK 계약이 달라 경로에 provider를 고정한다.
     * 다른 제공자가 생기면 `{provider}`로 묶지 말고 그 제공자의 경로를 따로 연다.
     */
    @Loggable
    @PostMapping("/api/v1/users/social-login/kakao/native")
    fun loginWithNativeToken(
        @Valid @RequestBody request: NativeSocialLoginRequest,
        response: HttpServletResponse,
    ): ApiResponse<NativeSocialLoginResponse> {
        val result = oAuthFacade.loginWithNativeToken(SocialProvider.KAKAO, request.accessToken)
        result.refreshToken?.let { refreshTokenCookieFactory.addTo(response, it) }

        return ApiResponse.ok(result.response)
    }
}
