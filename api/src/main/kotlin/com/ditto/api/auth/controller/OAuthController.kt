package com.ditto.api.auth.controller

import com.ditto.api.auth.facade.OAuthFacade
import com.ditto.api.config.FrontProperties
import com.ditto.domain.socialaccount.entity.SocialProvider
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI

@RestController
@RequestMapping("api/v1/users/social-login")
class OAuthController(
    private val oAuthFacade: OAuthFacade,
    private val frontProperties: FrontProperties,
) {

    @GetMapping("/{provider}")
    fun login(@PathVariable provider: SocialProvider): ResponseEntity<Unit> {
        val authorizationUrl = oAuthFacade.getAuthorizationUrl(provider)
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(authorizationUrl))
            .build()
    }

    @GetMapping("/{provider}/callback")
    fun callback(
        @PathVariable provider: SocialProvider,
        @RequestParam code: String,
    ): ResponseEntity<Unit> {
        val result = oAuthFacade.login(provider, code)
        val redirectUri = UriComponentsBuilder.fromUriString(frontProperties.url)
            .path(frontProperties.oauthCallbackPath)
            .apply {
                result.accessToken?.let { queryParam("accessToken", it) }
                result.refreshToken?.let { queryParam("refreshToken", it) }
            }
            .build()
            .toUri()
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(redirectUri)
            .build()
    }
}
