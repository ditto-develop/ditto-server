package com.ditto.infrastructure.oauth.kakao

data class KakaoUserResponse(
    val id: Long,
    val kakaoAccount: KakaoAccount?,
) {
    data class KakaoAccount(
        val profile: KakaoProfile?,
    )

    data class KakaoProfile(
        val nickname: String?,
    )
}
