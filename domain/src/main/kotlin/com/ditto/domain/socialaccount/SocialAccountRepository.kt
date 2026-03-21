package com.ditto.domain.socialaccount

import org.springframework.data.jpa.repository.JpaRepository

interface SocialAccountRepository : JpaRepository<SocialAccount, Long> {
    fun findByProviderAndProviderUserId(provider: SocialProvider, providerUserId: String): SocialAccount?
}
