package com.ditto.domain.socialaccount.entity

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException

enum class SocialProvider {
    KAKAO,
    ;

    companion object {
        fun from(value: String): SocialProvider {
            return entries.find { it.name.equals(value, ignoreCase = true) }
                ?: throw ErrorException(ErrorCode.UNSUPPORTED_PROVIDER)
        }
    }
}
