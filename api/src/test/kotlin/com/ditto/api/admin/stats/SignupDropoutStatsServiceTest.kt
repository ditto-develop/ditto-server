package com.ditto.api.admin.stats

import com.ditto.api.support.IntegrationTest
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.member.repository.querydsl.SocialProviderCount
import com.ditto.domain.socialaccount.entity.SocialAccount
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.domain.socialaccount.repository.SocialAccountRepository
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource

class SignupDropoutStatsServiceTest(
    private val signupDropoutStatsService: SignupDropoutStatsService,
    private val memberRepository: MemberRepository,
    private val socialAccountRepository: SocialAccountRepository,
    private val dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun saveMemberWithSocialAccount(nickname: String, status: MemberStatus, provider: SocialProvider): Member {
        val member = memberRepository.save(
            MemberFixture.create(
                nickname = nickname,
                email = "$nickname@ditto.pics",
                status = status,
                joinedAt = if (status == MemberStatus.PENDING) null else LocalDateTime.of(2026, 10, 20, 12, 0),
            ),
        )
        socialAccountRepository.save(
            SocialAccount.create(memberId = member.id, provider = provider, providerUserId = "$provider-$nickname"),
        )
        return member
    }

    fun moveCreatedAt(member: Member, createdAt: LocalDateTime) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("UPDATE member SET created_at = ? WHERE id = ?").use { statement ->
                statement.setObject(1, createdAt)
                statement.setLong(2, member.id)
                statement.executeUpdate()
            }
        }
    }

    "findStats" - {
        "가입을 시작하고 24시간이 지난 회원만 이탈로 세고 가입을 마친 회원은 로그인 수단별로 센다" {
            val dropout = saveMemberWithSocialAccount("dropout", MemberStatus.PENDING, SocialProvider.KAKAO)
            moveCreatedAt(dropout, LocalDateTime.now().minusHours(25))
            saveMemberWithSocialAccount("signing-up", MemberStatus.PENDING, SocialProvider.KAKAO)
            saveMemberWithSocialAccount("joined", MemberStatus.ACTIVE, SocialProvider.APPLE)

            val stats = signupDropoutStatsService.findStats()

            stats.dropoutHours shouldBe 24L
            stats.realToday shouldBe LocalDate.now()
            stats.signupDropouts.map { it.memberId } shouldContainExactly listOf(dropout.id)
            stats.joinedCountsBySocialProvider shouldContainExactly listOf(SocialProviderCount(SocialProvider.APPLE, 1L))
        }

        "소개노트가 3개보다 적은 이용 중인 회원과 이용 중인 회원 수를 함께 주고 더미는 뺀다" {
            val joined = saveMemberWithSocialAccount("joined", MemberStatus.ACTIVE, SocialProvider.KAKAO)
            saveMemberWithSocialAccount("left", MemberStatus.LEFT, SocialProvider.KAKAO)
            memberRepository.save(MemberFixture.create(nickname = "dummy-1", status = MemberStatus.ACTIVE))

            val stats = signupDropoutStatsService.findStats()

            stats.minIntroNoteCountForQuiz shouldBe 3L
            stats.quizBlockedMembers.map { it.memberId } shouldContainExactly listOf(joined.id)
            stats.activeStatusMemberCount shouldBe 1L
        }
    }
})
