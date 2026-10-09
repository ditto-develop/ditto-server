package com.ditto.api.user

import com.ditto.api.auth.service.MemberSocialAccountService
import com.ditto.api.support.IntegrationTest
import com.ditto.api.user.scheduler.DeferredLeaveScheduler
import com.ditto.api.user.service.DeferredLeaveService
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.entity.PersonalMatch
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.socialaccount.entity.SocialAccount
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.domain.socialaccount.repository.SocialAccountRepository
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import javax.sql.DataSource

class DeferredLeaveTest(
    private val deferredLeaveService: DeferredLeaveService,
    private val deferredLeaveScheduler: DeferredLeaveScheduler,
    private val memberSocialAccountService: MemberSocialAccountService,
    private val memberRepository: MemberRepository,
    private val socialAccountRepository: SocialAccountRepository,
    private val personalMatchRepository: PersonalMatchRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun saveActive(nickname: String): Member = memberRepository.save(Member(nickname = nickname).apply { activate() })

    fun saveDeferred(nickname: String, reason: String = "APPLE_ACCOUNT_DELETED"): Member =
        memberRepository.save(Member(nickname = nickname).apply { activate() }.apply { deferLeave(reason) })

    fun saveAcceptedMatch(memberId: Long): PersonalMatch {
        val partner = saveActive("상대-$memberId")
        return personalMatchRepository.save(
            PersonalMatchFixture.create(
                requesterId = memberId,
                receiverId = partner.id,
                status = PersonalMatchStatus.ACCEPTED,
            ),
        )
    }

    fun reload(member: Member): Member = memberRepository.findById(member.id).orElseThrow()

    "진행 중인 매칭이 끝나면 미뤄 둔 사유로 탈퇴시킨다" {
        val member = saveDeferred("대기회원")
        val match = saveAcceptedMatch(member.id)

        deferredLeaveService.leaveIfNothingInProgress(member.id) shouldBe false
        reload(member).status shouldBe MemberStatus.ACTIVE

        personalMatchRepository.delete(match)
        deferredLeaveService.leaveIfNothingInProgress(member.id) shouldBe true

        val left = reload(member)
        left.status shouldBe MemberStatus.LEFT
        left.leaveReason shouldBe "APPLE_ACCOUNT_DELETED"
        left.deferredLeaveReason.shouldBeNull()
    }

    "대기 중이 아닌 회원은 건드리지 않는다" {
        val member = saveActive("일반회원")

        deferredLeaveService.leaveIfNothingInProgress(member.id) shouldBe false

        reload(member).status shouldBe MemberStatus.ACTIVE
    }

    "대기 중인 회원만 찾는다" {
        val deferred = saveDeferred("대기중")
        saveActive("대기아님")

        deferredLeaveService.findDeferredMemberIds() shouldContainExactly listOf(deferred.id)
    }

    "스케줄러는 진행이 끝난 회원만 탈퇴시키고 나머지는 계속 기다리게 둔다" {
        val finished = saveDeferred("진행끝난회원")
        val stillMatching = saveDeferred("아직매칭중회원")
        saveAcceptedMatch(stillMatching.id)

        deferredLeaveScheduler.leaveDeferredMembers()

        reload(finished).status shouldBe MemberStatus.LEFT
        val waiting = reload(stillMatching)
        waiting.status shouldBe MemberStatus.ACTIVE
        waiting.isLeaveDeferred() shouldBe true
    }

    "대기 중에 같은 애플 계정으로 다시 로그인하면 대기가 풀린다" {
        val member = saveDeferred("다시온회원", reason = "APPLE_CONSENT_REVOKED")
        socialAccountRepository.save(
            SocialAccount.create(memberId = member.id, provider = SocialProvider.APPLE, providerUserId = "apple-sub-back"),
        )

        memberSocialAccountService.findOrCreateMember(
            provider = SocialProvider.APPLE,
            providerUserId = "apple-sub-back",
            email = null,
            birthDate = null,
        )

        reload(member).isLeaveDeferred() shouldBe false
        deferredLeaveService.leaveIfNothingInProgress(member.id) shouldBe false
        reload(member).status shouldBe MemberStatus.ACTIVE
    }
})
