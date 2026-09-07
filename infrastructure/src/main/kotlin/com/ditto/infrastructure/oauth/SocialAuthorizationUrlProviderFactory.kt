package com.ditto.infrastructure.oauth

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.domain.socialaccount.entity.SocialProvider

class SocialAuthorizationUrlProviderFactory(
    private val providerMap: Map<SocialProvider, SocialAuthorizationUrlProvider>,
) {
    fun getProvider(provider: SocialProvider): SocialAuthorizationUrlProvider = providerMap[provider]
        ?: throw ErrorException(ErrorCode.UNSUPPORTED_PROVIDER)
}
