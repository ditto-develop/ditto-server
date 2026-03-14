package com.ditto.common.exception

enum class ErrorCode(
    val status: Int,
    val code: String,
    val message: String,
) {
    BAD_REQUEST(400, "0001", "잘못된 요청값입니다."),
    UNAUTHORIZED_ERROR(401, "0002", "인가되지 않은 요청입니다."),
    INTERNAL_ERROR(500, "9999", "알 수 없는 에러가 발생했습니다."),
}
