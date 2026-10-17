package com.ditto.domain.feedback.entity

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException

// code는 FE와 주고받는 값이라 배포된 값은 이름·code를 바꾸거나 지우지 않는다.
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
