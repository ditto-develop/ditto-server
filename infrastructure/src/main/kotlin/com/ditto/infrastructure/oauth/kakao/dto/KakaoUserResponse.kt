package com.ditto.infrastructure.oauth.kakao.dto

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class KakaoUserResponse(
    val id: Long,
    val kakaoAccount: KakaoAccount?,
) {
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
    data class KakaoAccount(
        val profile: KakaoProfile?,
        val email: String?,
    )

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
    data class KakaoProfile(
        val nickname: String?,
    )
}
