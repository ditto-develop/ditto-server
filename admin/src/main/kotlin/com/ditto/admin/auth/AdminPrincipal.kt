package com.ditto.admin.auth

import java.io.Serializable

/**
 * 로그인한 어드민 식별 정보(세션 저장). 시각 오버라이드 등의 변경자(author) 기록에 이름·이메일을 사용한다.
 */
data class AdminPrincipal(
    val memberId: Long,
    val name: String?,
    val email: String?,
) : Serializable {
    /** 화면 표시용 라벨: "이름(이메일)" — 둘 다 없으면 회원 ID. */
    val displayName: String
        get() = when {
            !name.isNullOrBlank() && !email.isNullOrBlank() -> "$name($email)"
            !name.isNullOrBlank() -> name
            !email.isNullOrBlank() -> email
            else -> "회원 #$memberId"
        }

    companion object {
        private const val serialVersionUID = 1L
    }
}
