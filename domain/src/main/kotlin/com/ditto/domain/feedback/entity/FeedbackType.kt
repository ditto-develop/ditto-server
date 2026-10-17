package com.ditto.domain.feedback.entity

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException

/**
 * 의견 유형. [code]는 FE와 주고받는 식별자(kebab-case).
 * 값 추가 전용 — 배포된 값의 이름·코드 변경·삭제는 금지한다.
 */
enum class FeedbackType(
    val code: String,
    val description: String,
) {
    BUG("bug", "버그 제보"),
    SUGGESTION("suggestion", "개선 제안"),
    ETC("etc", "기타"),
    ;

    companion object {
        fun from(code: String): FeedbackType =
            entries.firstOrNull { it.code == code }
                ?: throw WarnException(ErrorCode.BAD_REQUEST, "지원하지 않는 의견 유형입니다: $code")
    }
}
