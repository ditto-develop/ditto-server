package com.ditto.domain.member.entity

/** 닉네임 형식 규칙. 가입과 프로필 수정이 같은 기준을 써야 해서 한곳에 둔다. */
object NicknamePolicy {
    const val MIN_LENGTH = 2
    const val MAX_LENGTH = 10
    const val PATTERN = "^[a-zA-Z0-9가-힣]+$"
    const val INVALID_MESSAGE = "닉네임은 한글·영문·숫자만 허용됩니다."
}
