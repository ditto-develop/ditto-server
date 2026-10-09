package com.ditto.infrastructure.oauth.apple

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import org.springframework.http.MediaType
import org.springframework.util.MultiValueMap
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.service.annotation.PostExchange

interface AppleAuthSender {

    @PostExchange(
        url = "https://appleid.apple.com/auth/token",
        contentType = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
    )
    fun getToken(@RequestBody params: MultiValueMap<String, String>): AppleTokenResponse

    @PostExchange(
        url = "https://appleid.apple.com/auth/revoke",
        contentType = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
    )
    fun revoke(@RequestBody params: MultiValueMap<String, String>)
}

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class AppleTokenResponse(
    val refreshToken: String? = null,
)
