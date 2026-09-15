package com.ditto.infrastructure.oauth.kakao.dto

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

/**
 * kauth.kakao.com 토큰 교환 에러 바디. 사용자 조회(kapi)는 {"msg","code"} 라 형식이 다르다.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class KakaoTokenErrorResponse(
    val error: String? = null,
    val errorCode: String? = null,
    val errorDescription: String? = null,
) {
    fun isInvalidGrant(): Boolean = error == INVALID_GRANT

    companion object {
        private const val INVALID_GRANT = "invalid_grant"
    }
}
