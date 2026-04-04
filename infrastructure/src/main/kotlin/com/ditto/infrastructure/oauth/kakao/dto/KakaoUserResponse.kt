package com.ditto.infrastructure.oauth.kakao.dto

data class KakaoUserResponse(
    val id: Long,
    val kakaoAccount: KakaoAccount?,
) {
    data class KakaoAccount(
        val profile: KakaoProfile?,
        val email: String?,
    )

    data class KakaoProfile(
        val nickname: String?,
    )
}
