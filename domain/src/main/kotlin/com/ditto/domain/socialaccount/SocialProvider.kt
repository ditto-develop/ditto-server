package com.ditto.domain.socialaccount

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException

enum class SocialProvider {
    KAKAO,
    ;

    companion object {
        fun from(value: String): SocialProvider {
            return entries.find { it.name.equals(value, ignoreCase = true) }
                ?: throw WarnException(ErrorCode.UNSUPPORTED_PROVIDER)
        }
    }
}
