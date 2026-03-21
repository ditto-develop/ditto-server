package com.ditto.api.oauth

import com.ditto.api.config.auth.JwtTokenProvider
import com.ditto.domain.member.Member
import com.ditto.domain.member.MemberRepository
import com.ditto.domain.socialaccount.SocialAccount
import com.ditto.domain.socialaccount.SocialAccountRepository
import com.ditto.domain.socialaccount.SocialProvider
import com.ditto.infrastructure.oauth.OAuthClientFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OAuthService(
    private val oAuthClientFactory: OAuthClientFactory,
    private val memberRepository: MemberRepository,
    private val socialAccountRepository: SocialAccountRepository,
    private val jwtTokenProvider: JwtTokenProvider,
) {
    fun getAuthorizationUrl(provider: SocialProvider): String =
        oAuthClientFactory.getClient(provider).getAuthorizationUrl()

    private fun getClient(provider: SocialProvider): OAuthClient =
        clientMap[provider] ?: throw WarnException(ErrorCode.UNSUPPORTED_PROVIDER)

    @Transactional
    fun login(provider: SocialProvider, code: String): OAuthLoginResponse {
        val client = oAuthClientFactory.getClient(provider)
        val accessToken = client.getAccessToken(code)
        val userInfo = client.getUserInfo(accessToken)

        val memberId = findOrCreateMember(provider, userInfo.id, userInfo.nickname)
        val token = jwtTokenProvider.generateToken(memberId)
        return OAuthLoginResponse(accessToken = token)
    }

    private fun findOrCreateMember(provider: SocialProvider, providerUserId: String, nickname: String): Long {
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
