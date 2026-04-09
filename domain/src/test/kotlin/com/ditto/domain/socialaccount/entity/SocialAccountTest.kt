package com.ditto.domain.socialaccount.entity

import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.socialaccount.repository.SocialAccountRepository
import com.ditto.domain.support.IntegrationTest
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
            }
        }
    },
)
