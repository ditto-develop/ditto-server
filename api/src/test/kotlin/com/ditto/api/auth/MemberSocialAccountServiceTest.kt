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
                    SocialProvider.KAKAO, "kakao-123", "테스트유저", "test@kakao.com",
                )

                member.id shouldNotBe 0L
                member.nickname shouldBe "테스트유저"
                member.email shouldBe "test@kakao.com"
                member.status shouldBe MemberStatus.PENDING
                memberRepository.count() shouldBe 1
                socialAccountRepository.count() shouldBe 1
            }

            "기존 사용자면 기존 Member를 반환한다" {
                val created = memberSocialAccountService.findOrCreateMember(
                    SocialProvider.KAKAO, "kakao-123", "테스트유저", "test@kakao.com",
                )

                val found = memberSocialAccountService.findOrCreateMember(
                    SocialProvider.KAKAO, "kakao-123", "다른닉네임", "test@kakao.com",
                )

                found.id shouldBe created.id
                memberRepository.count() shouldBe 1
                socialAccountRepository.count() shouldBe 1
            }

            "기존 사용자 재로그인 시 이메일이 변경되었으면 갱신한다" {
                val created = memberSocialAccountService.findOrCreateMember(
                    SocialProvider.KAKAO, "kakao-123", "테스트유저", "old@kakao.com",
                )

                val found = memberSocialAccountService.findOrCreateMember(
                    SocialProvider.KAKAO, "kakao-123", "테스트유저", "new@kakao.com",
                )

                found.id shouldBe created.id
                found.email shouldBe "new@kakao.com"
            }

            "기존 사용자 재로그인 시 이메일이 null이면 기존 이메일을 유지한다" {
                val created = memberSocialAccountService.findOrCreateMember(
                    SocialProvider.KAKAO, "kakao-123", "테스트유저", "old@kakao.com",
                )

                val found = memberSocialAccountService.findOrCreateMember(
                    SocialProvider.KAKAO, "kakao-123", "테스트유저", null,
                )

                found.id shouldBe created.id
                found.email shouldBe "old@kakao.com"
            }

            "신규 사용자 이메일 없이 가입할 수 있다" {
                val member = memberSocialAccountService.findOrCreateMember(
                    SocialProvider.KAKAO, "kakao-456", "테스트유저", null,
                )

                member.email shouldBe null
            }

            "기존 사용자 재조회 시 SocialAccount가 추가 생성되지 않는다" {
                memberSocialAccountService.findOrCreateMember(
                    SocialProvider.KAKAO, "kakao-123", "테스트유저", "test@kakao.com",
                )

                memberSocialAccountService.findOrCreateMember(
                    SocialProvider.KAKAO, "kakao-123", "테스트유저", "test@kakao.com",
                )

                socialAccountRepository.count() shouldBe 1
            }

            "SocialAccount는 있지만 Member가 없으면 예외가 발생한다" {
                val member = memberRepository.save(Member(nickname = "테스트유저", email = "test@kakao.com"))
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
                        SocialProvider.KAKAO, "kakao-123", "테스트유저", "test@kakao.com",
                    )
                }
            }
        }
    },
)
