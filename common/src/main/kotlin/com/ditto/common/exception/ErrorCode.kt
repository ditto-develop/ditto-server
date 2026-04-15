package com.ditto.common.exception

enum class ErrorCode(
    val status: Int,
    val code: String,
    val message: String,
) {
    BAD_REQUEST(400, "0001", "잘못된 요청값입니다."),
    UNAUTHORIZED_ERROR(401, "0002", "인가되지 않은 요청입니다."),
    FORBIDDEN(403, "0003", "접근 권한이 없습니다."),
    NOT_FOUND(404, "0004", "존재하지 않는 리소스입니다."),
    UNSUPPORTED_PROVIDER(400, "1001", "지원하지 않는 소셜 로그인 제공자입니다."),
    REFRESH_TOKEN_NOT_FOUND(401, "2001", "리프레시 토큰이 존재하지 않습니다."),
    REFRESH_TOKEN_EXPIRED(401, "2002", "리프레시 토큰이 만료되었습니다."),
    MEMBER_ALREADY_EXISTS(409, "3002", "이미 존재하는 사용자입니다."),
    NICKNAME_ALREADY_EXISTS(409, "3003", "이미 사용 중인 닉네임입니다."),
    QUIZ_NOT_IN_ACTIVE_SET(400, "4001", "현재 활성화된 퀴즈 세트에 속한 퀴즈가 아닙니다."),
    INVALID_CHOICE(400, "4002", "해당 퀴즈의 유효한 선택지가 아닙니다."),
    QUIZ_ALREADY_COMPLETED(400, "4003", "이미 완료된 퀴즈는 수정할 수 없습니다."),
    QUIZ_NOT_AVAILABLE_DAY(400, "4004", "퀴즈 참여 가능한 요일이 아닙니다."),
    INTERNAL_ERROR(500, "9999", "알 수 없는 에러가 발생했습니다."),
}
