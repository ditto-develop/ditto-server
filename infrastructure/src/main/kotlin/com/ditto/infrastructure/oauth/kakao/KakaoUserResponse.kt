package com.ditto.infrastructure.oauth.kakao

import com.fasterxml.jackson.annotation.JsonProperty

data class KakaoUserResponse(
    val id: Long,
    @JsonProperty("kakao_account")
    val kakaoAccount: KakaoAccount?,
) {
    data class KakaoAccount(
        val profile: KakaoProfile?,
    )

    data class KakaoProfile(
        val nickname: String?,
    )
}
