package com.ditto.api.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * App Store 심사용 데모 로그인 설정.
 *
 * 비밀번호는 평문이 아니라 **BCrypt 해시**로만 받는다. 레포가 공개라 `.aws/task-definition.json` 에
 * 들어가는 값은 모두 공개되기 때문이다. 둘 중 하나라도 비어 있으면 기능이 꺼진다.
 */
@ConfigurationProperties(prefix = "ditto.review-login")
data class ReviewLoginProperties(
    val loginId: String = "",
    val passwordHash: String = "",
) {
    val enabled: Boolean
        get() = loginId.isNotBlank() && passwordHash.isNotBlank()
}
