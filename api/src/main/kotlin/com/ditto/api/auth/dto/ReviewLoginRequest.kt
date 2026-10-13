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

    /** BCrypt 는 앞 72바이트만 대조하고 나머지는 무시한다. 길이 제한은 그 근처의 느슨한 상한이다(글자 수 기준). */
    @field:Mask
    @field:NotBlank
    @field:Size(max = 72)
    val password: String,
)
