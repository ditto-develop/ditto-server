package com.ditto.api.auth.dto

import com.ditto.common.logging.Mask
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * App Store 심사용 데모 로그인 요청.
 *
 * [password]는 로그에 남기지 않는다([Mask]). 이 객체를 서비스까지 그대로 넘기는 이유도 같다 —
 * 문자열로 풀어 넘기면 `@Loggable` 진입점 아래 내부 호출 로그에 평문이 찍힌다.
 */
data class ReviewLoginRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val loginId: String,

    /** BCrypt 는 72바이트까지만 본다. 그보다 길면 대조 자체가 거부된다. */
    @field:Mask
    @field:NotBlank
    @field:Size(max = 72)
    val password: String,
)
