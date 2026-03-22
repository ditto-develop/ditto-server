package com.ditto.infrastructure.oauth.kakao

import org.springframework.http.MediaType
import org.springframework.util.MultiValueMap
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.service.annotation.GetExchange
import org.springframework.web.service.annotation.PostExchange

interface KakaoApiSender {

    @PostExchange(
        url = "https://kauth.kakao.com/oauth/token",
        contentType = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
    )
    fun getToken(@RequestBody params: MultiValueMap<String, String>): KakaoTokenResponse

    @GetExchange(url = "https://kapi.kakao.com/v2/user/me")
    fun getUserInfo(@RequestHeader("Authorization") authorization: String): KakaoUserResponse
}
