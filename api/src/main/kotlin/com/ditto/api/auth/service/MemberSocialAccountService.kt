package com.ditto.api.auth.service

import com.ditto.api.auth.NicknameGenerator
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.socialaccount.entity.SocialAccount
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.domain.socialaccount.repository.SocialAccountRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import org.springframework.beans.factory.annotation.Value

@Service
class MemberSocialAccountService(
    private val memberRepository: MemberRepository,
    private val socialAccountRepository: SocialAccountRepository,
    @Value("\${ditto.member.purge.retention-days:30}") private val retentionDays: Long,
) {
    @Transactional
    fun findOrCreateMember(
        provider: SocialProvider,
        providerUserId: String,
        email: String?,
        birthDate: LocalDateTime?,
        name: String? = null,
        phoneNumber: String? = null,
        gender: Gender? = null,
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
            // 비즈 앱 전환으로 동의항목이 열리면 재로그인만으로 값이 채워진다(미동의 항목은 null이라 무시된다).
            member.updateOAuthInfo(
                name = name,
                phoneNumber = phoneNumber,
                gender = gender,
                birthDate = birthDate,
            )
            restoreIfWithinRetention(member)
            return member
        }

        val newMember = memberRepository.save(
            Member(
                nickname = generateUniqueNickname(),
                email = email,
                birthDate = birthDate,
                name = name,
                phoneNumber = phoneNumber,
                gender = gender,
            ),
        )
        socialAccountRepository.save(
            SocialAccount.create(
                memberId = newMember.id,
                provider = provider,
                providerUserId = providerUserId,
            ),
        )
        return newMember
    }

    /**
     * 탈퇴한 회원이 보존 기간 안에 재가입하면 계정을 복구한다 —
     * "탈퇴 후 30일 이내 재가입하면 계정을 복구할 수 있습니다"(피그마 6.2.4).
     *
     * 보존 기간이 지났는데 삭제 배치가 아직 돌지 않아 행이 남아 있는 경우는 복구하지 않는다.
     * 이때는 LEFT 상태가 유지되므로 인증 게이트가 막고, 배치가 정리한 뒤 새 회원으로 가입하게 된다.
     */
    private fun restoreIfWithinRetention(member: Member) {
        if (!member.isLeft()) return
        if (member.isRetentionExpiredAt(LocalDateTime.now(), retentionDays)) {
            log.info { "보존 기간이 지난 탈퇴 회원(id=${member.id})의 재로그인 — 복구하지 않는다" }
            return
        }
        log.info { "탈퇴 회원(id=${member.id}) 복구 (leftAt=${member.leftAt})" }
        member.restore()
    }

    /**
     * 소셜 계정에 연결된 회원을 조회한다(없으면 null). 어드민 로그인처럼 신규 가입 없이
     * 기존 회원만 식별해야 하는 경우 사용한다.
     */
    @Transactional(readOnly = true)
    fun findMemberBySocial(provider: SocialProvider, providerUserId: String): Member? {
        val account = socialAccountRepository.findByProviderAndProviderUserId(provider, providerUserId) ?: return null
        return memberRepository.findById(account.memberId).orElse(null)
    }

    private fun generateUniqueNickname(): String {
        repeat(5) {
            val nickname = NicknameGenerator.generate()
            if (!memberRepository.existsByNickname(nickname)) return nickname
        }
        return NicknameGenerator.generate()
    }

    companion object {
        private val log = KotlinLogging.logger {}
    }
}
