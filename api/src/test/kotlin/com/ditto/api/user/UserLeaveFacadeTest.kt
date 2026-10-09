package com.ditto.api.user

import com.ditto.api.support.IntegrationTest
import com.ditto.api.user.dto.LeaveRequest
import com.ditto.api.user.facade.UserLeaveFacade
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.socialaccount.entity.SocialAccount
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.domain.socialaccount.repository.SocialAccountRepository
import com.ditto.infrastructure.oauth.apple.AppleTokenClient
import com.ditto.infrastructure.oauth.apple.AppleTokenFakeClient
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import javax.sql.DataSource

class UserLeaveFacadeTest(
    private val userLeaveFacade: UserLeaveFacade,
    private val memberRepository: MemberRepository,
    private val socialAccountRepository: SocialAccountRepository,
    private val personalMatchRepository: PersonalMatchRepository,
    appleTokenClient: AppleTokenClient,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val fakeTokenClient = appleTokenClient as AppleTokenFakeClient

    fun saveAppleMember(nickname: String, refreshToken: String?): Pair<Member, SocialAccount> {
        val member = memberRepository.save(Member(nickname = nickname).apply { activate() })
        val account = SocialAccount.create(memberId = member.id, provider = SocialProvider.APPLE, providerUserId = "sub-$nickname")
        refreshToken?.let { account.storeProviderToken(refreshToken = it, clientId = "pics.ditto.app") }
        return member to socialAccountRepository.save(account)
    }

    fun reloadAccount(account: SocialAccount): SocialAccount = socialAccountRepository.findById(account.id).orElseThrow()

    "탈퇴하면 저장해 둔 애플 토큰을 폐기하고 지운다" {
        val (member, account) = saveAppleMember("애플탈퇴회원", refreshToken = "apple-refresh-leave")

        userLeaveFacade.leave(member.id, member.id, LeaveRequest(reason = "etc"))

        memberRepository.findById(member.id).orElseThrow().status shouldBe MemberStatus.LEFT
        fakeTokenClient.revokedTokens shouldContain "apple-refresh-leave"
        reloadAccount(account).providerRefreshToken.shouldBeNull()
    }

    "저장된 애플 토큰이 없으면 폐기 없이 탈퇴만 한다" {
        val (member, _) = saveAppleMember("토큰없는회원", refreshToken = null)

        userLeaveFacade.leave(member.id, member.id, LeaveRequest(reason = "etc"))

        memberRepository.findById(member.id).orElseThrow().status shouldBe MemberStatus.LEFT
    }

    "탈퇴가 막히면 애플 토큰도 폐기하지 않는다" {
        val (member, account) = saveAppleMember("매칭중애플회원", refreshToken = "apple-refresh-blocked")
        val partner = memberRepository.save(Member(nickname = "매칭상대").apply { activate() })
        personalMatchRepository.save(
            PersonalMatchFixture.create(
                requesterId = member.id,
                receiverId = partner.id,
                status = PersonalMatchStatus.ACCEPTED,
            ),
        )

        val exception = shouldThrow<WarnException> {
            userLeaveFacade.leave(member.id, member.id, LeaveRequest(reason = "etc"))
        }

        exception.errorCode shouldBe ErrorCode.CANNOT_LEAVE_WHILE_IN_PROGRESS
        fakeTokenClient.revokedTokens shouldNotContain "apple-refresh-blocked"
        reloadAccount(account).providerRefreshToken shouldBe "apple-refresh-blocked"
    }
})
