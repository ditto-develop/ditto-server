package com.ditto.api.auth.service

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.socialaccount.entity.SocialAccount
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.domain.socialaccount.repository.SocialAccountRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MemberSocialAccountService(
    private val memberRepository: MemberRepository,
    private val socialAccountRepository: SocialAccountRepository,
) {
    @Transactional
    fun findOrCreateMember(
        provider: SocialProvider,
        providerUserId: String,
        nickname: String,
        email: String?,
    ): Member {
        val existingAccount = socialAccountRepository.findByProviderAndProviderUserId(provider, providerUserId)

        if (existingAccount != null) {
            val member = memberRepository.findById(existingAccount.memberId).orElseThrow {
                log.error {
                    "SocialAccount(id=${existingAccount.id})에 연결된 Member(id=${existingAccount.memberId})가 존재하지 않습니다."
                }
                ErrorException(ErrorCode.INTERNAL_ERROR)
            }
            if (member.hasEmailChanged(email)) {
                log.info { "Member(id=${member.id}) 이메일 변경: ${member.email} -> $email" }
                member.updateEmail(email)
            }
            return member
        }

        val newMember = memberRepository.save(Member(nickname = nickname, email = email))
        socialAccountRepository.save(
            SocialAccount.create(
                memberId = newMember.id,
                provider = provider,
                providerUserId = providerUserId,
            ),
        )
        return newMember
    }

    companion object {
        private val log = KotlinLogging.logger {}
    }
}
