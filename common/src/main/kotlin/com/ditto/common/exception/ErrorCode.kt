package com.ditto.common.exception

enum class ErrorCode(
    val status: Int,
    val code: String,
    val message: String,
) {
    BAD_REQUEST(400, "0001", "잘못된 요청값입니다."),
    UNAUTHORIZED_ERROR(401, "0002", "인가되지 않은 요청입니다."),
    UNSUPPORTED_PROVIDER(400, "1001", "지원하지 않는 소셜 로그인 제공자입니다."),
    REFRESH_TOKEN_NOT_FOUND(401, "2001", "리프레시 토큰이 존재하지 않습니다."),
    REFRESH_TOKEN_EXPIRED(401, "2002", "리프레시 토큰이 만료되었습니다."),
    INTERNAL_ERROR(500, "9999", "알 수 없는 에러가 발생했습니다."),
}
