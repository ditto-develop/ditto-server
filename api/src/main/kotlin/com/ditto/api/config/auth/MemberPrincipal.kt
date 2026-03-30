package com.ditto.api.config.auth

import com.ditto.domain.socialaccount.entity.SocialProvider

data class MemberPrincipal(
    val providerUserId: String,
    val provider: SocialProvider,
)
