package com.ditto.api.oauth

import com.ditto.common.response.ApiResponse
import com.ditto.domain.socialaccount.SocialProvider
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@RequestMapping("/api/v1/users/social-login")
class OAuthController(
    private val oAuthService: OAuthService,
) {

    @GetMapping("/{provider}")
    fun login(@PathVariable provider: String): ResponseEntity<Unit> {
        val socialProvider = SocialProvider.from(provider)
        val authorizationUrl = oAuthService.getAuthorizationUrl(socialProvider)
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(authorizationUrl))
            .build()
    }

    @GetMapping("/{provider}/callback")
    fun callback(
        @PathVariable provider: String,
        @RequestParam code: String,
    ): ApiResponse<OAuthLoginResponse> {
        val socialProvider = SocialProvider.from(provider)
        val result = oAuthService.login(socialProvider, code)
        return ApiResponse.ok(result)
    }
}
