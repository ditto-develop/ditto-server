package com.ditto.api.auth

import com.ditto.api.auth.service.MemberSocialAccountService
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorException
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.socialaccount.entity.SocialAccount
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.domain.socialaccount.repository.SocialAccountRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import javax.sql.DataSource

class MemberSocialAccountServiceTest(
    private val memberSocialAccountService: MemberSocialAccountService,
    private val memberRepository: MemberRepository,
    private val socialAccountRepository: SocialAccountRepository,
    dataSource: DataSource,
) : IntegrationTest(
    dataSource,
    {

        "findOrCreateMember" - {
            "신규 사용자면 PENDING 상태의 Member와 SocialAccount를 생성한다" {
                val member = memberSocialAccountService.findOrCreateMember(
                    SocialProvider.KAKAO, "kakao-123", "테스트유저",
                )

                member.id shouldNotBe 0L
                member.nickname shouldBe "테스트유저"
                member.status shouldBe MemberStatus.PENDING
                memberRepository.count() shouldBe 1
                socialAccountRepository.count() shouldBe 1
            }

            "기존 사용자면 기존 Member를 반환한다" {
                val created = memberSocialAccountService.findOrCreateMember(
                    SocialProvider.KAKAO, "kakao-123", "테스트유저",
                )

                val found = memberSocialAccountService.findOrCreateMember(
                    SocialProvider.KAKAO, "kakao-123", "다른닉네임",
                )

                found.id shouldBe created.id
                memberRepository.count() shouldBe 1
                socialAccountRepository.count() shouldBe 1
            }

            "기존 사용자 재조회 시 SocialAccount가 추가 생성되지 않는다" {
                memberSocialAccountService.findOrCreateMember(
                    SocialProvider.KAKAO, "kakao-123", "테스트유저",
                )

                memberSocialAccountService.findOrCreateMember(
                    SocialProvider.KAKAO, "kakao-123", "테스트유저",
                )

                socialAccountRepository.count() shouldBe 1
            }

            "SocialAccount는 있지만 Member가 없으면 예외가 발생한다" {
                val member = memberRepository.save(Member(nickname = "테스트유저"))
                socialAccountRepository.save(
                    SocialAccount.create(
                        memberId = member.id,
                        provider = SocialProvider.KAKAO,
                        providerUserId = "kakao-123",
                    ),
                )
                memberRepository.deleteById(member.id)

                shouldThrow<ErrorException> {
                    memberSocialAccountService.findOrCreateMember(
                        SocialProvider.KAKAO, "kakao-123", "테스트유저",
                    )
                }
            }
        }
    },
)
