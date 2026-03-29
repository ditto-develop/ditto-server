package com.ditto.domain.socialaccount.repository

import com.ditto.domain.socialaccount.entity.SocialAccount
import com.ditto.domain.socialaccount.entity.SocialProvider
import org.springframework.data.jpa.repository.JpaRepository

interface SocialAccountRepository : JpaRepository<SocialAccount, Long> {
    fun findByProviderAndProviderUserId(provider: SocialProvider, providerUserId: String): SocialAccount?
    fun findByMemberId(memberId: Long): SocialAccount?
}
