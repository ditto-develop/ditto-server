package com.ditto.api.oauth

import com.ditto.api.config.auth.JwtTokenProvider
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.member.Member
import com.ditto.domain.member.MemberRepository
import com.ditto.domain.socialaccount.SocialAccount
import com.ditto.domain.socialaccount.SocialAccountRepository
import com.ditto.domain.socialaccount.SocialProvider
import com.ditto.infrastructure.oauth.OAuthClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OAuthService(
    oAuthClients: List<OAuthClient>,
    private val memberRepository: MemberRepository,
    private val socialAccountRepository: SocialAccountRepository,
    private val jwtTokenProvider: JwtTokenProvider,
) {
    private val clientMap = oAuthClients.associateBy { it.getProvider() }

    fun getAuthorizationUrl(provider: SocialProvider): String {
        return getClient(provider).getAuthorizationUrl()
    }

    @Transactional
    fun login(provider: SocialProvider, code: String): OAuthLoginResponse {
        val client = getClient(provider)
        val accessToken = client.getAccessToken(code)
        val userInfo = client.getUserInfo(accessToken)

        val socialAccount = socialAccountRepository.findByProviderAndProviderUserId(provider, userInfo.id)

        val memberId = if (socialAccount != null) {
            socialAccount.memberId
        } else {
            val newMember = memberRepository.save(Member(nickname = userInfo.nickname))
            socialAccountRepository.save(
                SocialAccount.create(
                    memberId = newMember.id,
                    provider = provider,
                    providerUserId = userInfo.id,
                ),
            )
            newMember.id
        }

        val token = jwtTokenProvider.generateToken(memberId)
        return OAuthLoginResponse(accessToken = token)
    }

    private fun getClient(provider: SocialProvider): OAuthClient {
        return clientMap[provider]
            ?: throw WarnException(ErrorCode.UNSUPPORTED_PROVIDER)
    }
}
