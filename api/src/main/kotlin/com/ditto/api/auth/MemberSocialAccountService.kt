package com.ditto.api.auth

import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.socialaccount.entity.SocialAccount
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.domain.socialaccount.repository.SocialAccountRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MemberSocialAccountService(
    private val memberRepository: MemberRepository,
    private val socialAccountRepository: SocialAccountRepository,
) {
    @Transactional
    fun findOrCreateMember(provider: SocialProvider, providerUserId: String, nickname: String): Long {
        val existingAccount = socialAccountRepository.findByProviderAndProviderUserId(provider, providerUserId)

        if (existingAccount != null) {
            return existingAccount.memberId
        }

        val newMember = memberRepository.save(Member(nickname = nickname))
        socialAccountRepository.save(
            SocialAccount.create(
                memberId = newMember.id,
                provider = provider,
                providerUserId = providerUserId,
            ),
        )
        return newMember.id
    }
}
