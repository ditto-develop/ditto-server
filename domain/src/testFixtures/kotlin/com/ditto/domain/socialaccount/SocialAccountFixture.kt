package com.ditto.domain.socialaccount

import com.ditto.domain.socialaccount.entity.SocialAccount
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.domain.withId

object SocialAccountFixture {

    fun create(
        memberId: Long = 1L,
        provider: SocialProvider = SocialProvider.KAKAO,
        providerUserId: String = "12345",
        id: Long = 0L,
    ): SocialAccount = SocialAccount.create(
        memberId = memberId,
        provider = provider,
        providerUserId = providerUserId,
    ).withId(id)
}
