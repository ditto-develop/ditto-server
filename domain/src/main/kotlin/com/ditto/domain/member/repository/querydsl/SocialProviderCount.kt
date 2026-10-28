package com.ditto.domain.member.repository.querydsl

import com.ditto.domain.socialaccount.entity.SocialProvider

data class SocialProviderCount(
    val socialProvider: SocialProvider?,
    val count: Long,
)
