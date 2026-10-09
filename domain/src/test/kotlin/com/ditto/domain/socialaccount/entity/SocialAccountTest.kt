package com.ditto.domain.socialaccount.entity

import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.socialaccount.repository.SocialAccountRepository
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import javax.sql.DataSource

class SocialAccountTest(
    private val memberRepository: MemberRepository,
    private val socialAccountRepository: SocialAccountRepository,
    dataSource: DataSource,
) : IntegrationTest(
    dataSource,
    {
        "SocialAccount 생성" - {
            "create로 SocialAccount를 생성할 수 있다" {
                val member = memberRepository.save(Member(nickname = "테스트", email = "test@kakao.com"))
                val socialAccount = SocialAccount.create(
                    memberId = member.id,
                    provider = SocialProvider.KAKAO,
                    providerUserId = "kakao-123",
                )

                val saved = socialAccountRepository.save(socialAccount)

                saved.id shouldNotBe 0L
                saved.memberId shouldBe member.id
                saved.provider shouldBe SocialProvider.KAKAO
                saved.providerUserId shouldBe "kakao-123"
                saved.providerRefreshToken.shouldBeNull()
            }
        }

        "제공자 토큰" - {
            "토큰과 받은 client_id 를 함께 저장하고 비울 수 있다" {
                val member = memberRepository.save(Member(nickname = "애플회원"))
                val account = socialAccountRepository.save(
                    SocialAccount.create(memberId = member.id, provider = SocialProvider.APPLE, providerUserId = "apple-1"),
                )

                account.storeProviderToken(refreshToken = "apple-refresh", clientId = "pics.ditto.app")
                val stored = socialAccountRepository.save(account)
                stored.providerRefreshToken shouldBe "apple-refresh"
                stored.providerClientId shouldBe "pics.ditto.app"

                stored.clearProviderToken()
                val cleared = socialAccountRepository.save(stored)
                cleared.providerRefreshToken.shouldBeNull()
                cleared.providerClientId.shouldBeNull()
            }
        }
    },
)
