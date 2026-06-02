package com.ditto.api.auth.controller

import com.ditto.api.auth.facade.OAuthFacade
import com.ditto.api.config.auth.RefreshTokenCookieFactory
import com.ditto.domain.socialaccount.entity.SocialProvider
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@RequestMapping("api/v1/users/social-login")
class OAuthController(
    private val oAuthFacade: OAuthFacade,
    private val refreshTokenCookieFactory: RefreshTokenCookieFactory,
) {

    @GetMapping("/{provider}")
    fun login(
        @PathVariable provider: SocialProvider,
    ): ResponseEntity<Unit> {
        val authorizationUrl = oAuthFacade.getAuthorizationUrl(provider)
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(authorizationUrl))
            .build()
    }

    @GetMapping("/{provider}/callback")
    fun callback(
        @PathVariable provider: SocialProvider,
        @RequestParam code: String,
        response: HttpServletResponse,
    ): ResponseEntity<Unit> {
        val oauthLoginResult = oAuthFacade.login(provider, code)
        refreshTokenCookieFactory.addTo(response, oauthLoginResult.refreshToken)

        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(oauthLoginResult.redirectUrl))
            .build()
    }
}
