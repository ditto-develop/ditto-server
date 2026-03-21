package com.ditto.infrastructure.oauth

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.domain.socialaccount.SocialProvider

class OAuthClientFactory(
    private val clientMap: Map<SocialProvider, OAuthClient>,
) {
    fun getClient(provider: SocialProvider): OAuthClient = clientMap[provider]
        ?: throw ErrorException(ErrorCode.UNSUPPORTED_PROVIDER)

}
