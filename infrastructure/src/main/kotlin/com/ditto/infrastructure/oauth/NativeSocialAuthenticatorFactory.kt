package com.ditto.infrastructure.oauth

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.domain.socialaccount.entity.SocialProvider

class NativeSocialAuthenticatorFactory(
    private val authenticatorMap: Map<SocialProvider, NativeSocialAuthenticator>,
) {
    fun getAuthenticator(provider: SocialProvider): NativeSocialAuthenticator = authenticatorMap[provider]
        ?: throw ErrorException(ErrorCode.UNSUPPORTED_PROVIDER)
}
