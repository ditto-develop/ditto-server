package com.ditto.api.user

import com.ditto.api.auth.service.AuthService
import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.api.support.IntegrationTest
import com.ditto.api.user.dto.CreateUserRequest
import com.ditto.api.user.service.UserService
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.common.exception.WarnException
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.refreshtoken.repository.RefreshTokenRepository
import com.ditto.domain.socialaccount.entity.SocialAccount
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.domain.socialaccount.repository.SocialAccountRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import javax.sql.DataSource

class UserServiceTest(
    private val userService: UserService,
    private val authService: AuthService,
    private val memberRepository: MemberRepository,
    private val socialAccountRepository: SocialAccountRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    dataSource: DataSource,
) : IntegrationTest(
    dataSource,
    {

        "회원가입" - {
            "PENDING 상태의 회원을 정상 등록한다" {
                val member = memberRepository.save(Member(nickname = "임시닉네임"))
                socialAccountRepository.save(SocialAccount.create(member.id, SocialProvider.KAKAO, "provider-user-1"))

                val result = userService.register(
                    CreateUserRequest(
                        name = "김철수",
                        nickname = "철수123",
                        phoneNumber = "010-1234-5678",
                        gender = Gender.MALE,
                        age = 25,
                        provider = SocialProvider.KAKAO,
                        providerUserId = "provider-user-1",
                    ),
                )

                result.name shouldBe "김철수"
                result.nickname shouldBe "철수123"
                result.phoneNumber shouldBe "010-1234-5678"
                result.gender shouldBe Gender.MALE.name
                result.age shouldBe 25
                result.joinedAt shouldNotBe null

                val saved = memberRepository.findById(member.id).get()
                saved.status shouldBe MemberStatus.ACTIVE
            }

            "존재하지 않는 소셜 계정이면 예외가 발생한다" {
                val exception = shouldThrow<WarnException> {
                    userService.register(
                        CreateUserRequest(
                            provider = SocialProvider.KAKAO,
                            providerUserId = "non-existent",
                        ),
                    )
                }
                exception.errorCode shouldBe ErrorCode.NOT_FOUND
            }

            "이미 ACTIVE인 회원이면 예외가 발생한다" {
                val member = memberRepository.save(Member(nickname = "임시닉네임"))
                member.activate()
                memberRepository.save(member)
                socialAccountRepository.save(SocialAccount.create(member.id, SocialProvider.KAKAO, "provider-user-2"))

                val exception = shouldThrow<WarnException> {
                    userService.register(
                        CreateUserRequest(
                            provider = SocialProvider.KAKAO,
                            providerUserId = "provider-user-2",
                        ),
                    )
                }
                exception.errorCode shouldBe ErrorCode.MEMBER_ALREADY_EXISTS
            }

            "이미 사용 중인 닉네임이면 예외가 발생한다" {
                val existingMember = memberRepository.save(Member(nickname = "중복닉네임"))
                existingMember.activate()
                memberRepository.save(existingMember)

                val pending = memberRepository.save(Member(nickname = "임시닉네임"))
                socialAccountRepository.save(SocialAccount.create(pending.id, SocialProvider.KAKAO, "provider-user-3"))

                val exception = shouldThrow<WarnException> {
                    userService.register(
                        CreateUserRequest(
                            nickname = "중복닉네임",
                            provider = SocialProvider.KAKAO,
                            providerUserId = "provider-user-3",
                        ),
                    )
                }
                exception.errorCode shouldBe ErrorCode.NICKNAME_ALREADY_EXISTS
            }
        }

        "닉네임 중복 확인" - {
            "사용 가능한 닉네임이면 available true를 반환한다" {
                val result = userService.checkNicknameAvailability("새닉네임")

                result.available shouldBe true
            }

            "이미 사용 중인 닉네임이면 예외가 발생한다" {
                memberRepository.save(Member(nickname = "사용중닉네임"))

                val exception = shouldThrow<WarnException> {
                    userService.checkNicknameAvailability("사용중닉네임")
                }
                exception.errorCode shouldBe ErrorCode.NICKNAME_ALREADY_EXISTS
            }
        }

        "회원 탈퇴" - {
            "탈퇴 시 회원이 삭제된다" {
                val member = memberRepository.save(Member(nickname = "탈퇴유저"))
                socialAccountRepository.save(SocialAccount.create(member.id, SocialProvider.KAKAO, "leave-user"))
                authService.createRefreshToken(member.id)

                val result = userService.leaveUser(
                    id = member.id,
                    principal = MemberPrincipal(providerUserId = "leave-user", provider = SocialProvider.KAKAO),
                )

                result.id shouldBe member.id
                memberRepository.findById(member.id).isEmpty shouldBe true
            }

            "탈퇴 시 해당 회원의 모든 리프레시 토큰이 삭제된다" {
                val member = memberRepository.save(Member(nickname = "탈퇴유저"))
                socialAccountRepository.save(SocialAccount.create(member.id, SocialProvider.KAKAO, "leave-user-2"))
                val token1 = authService.createRefreshToken(member.id)
                val token2 = authService.createRefreshToken(member.id)

                userService.leaveUser(
                    id = member.id,
                    principal = MemberPrincipal(providerUserId = "leave-user-2", provider = SocialProvider.KAKAO),
                )

                refreshTokenRepository.findByToken(token1.token) shouldBe null
                refreshTokenRepository.findByToken(token2.token) shouldBe null
            }

            "존재하지 않는 회원이면 예외가 발생한다" {
                val exception = shouldThrow<WarnException> {
                    userService.leaveUser(
                        id = 99999L,
                        principal = MemberPrincipal(providerUserId = "any", provider = SocialProvider.KAKAO),
                    )
                }
                exception.errorCode shouldBe ErrorCode.NOT_FOUND
            }

            "다른 회원의 ID로 탈퇴 요청하면 예외가 발생한다" {
                val memberA = memberRepository.save(Member(nickname = "멤버A"))
                socialAccountRepository.save(SocialAccount.create(memberA.id, SocialProvider.KAKAO, "user-a"))
                val memberB = memberRepository.save(Member(nickname = "멤버B"))

                val exception = shouldThrow<WarnException> {
                    userService.leaveUser(
                        id = memberB.id,
                        principal = MemberPrincipal(providerUserId = "user-a", provider = SocialProvider.KAKAO),
                    )
                }
                exception.errorCode shouldBe ErrorCode.FORBIDDEN
            }

            "소셜 계정이 없으면 예외가 발생한다" {
                val member = memberRepository.save(Member(nickname = "소셜없는유저"))

                val exception = shouldThrow<ErrorException> {
                    userService.leaveUser(
                        id = member.id,
                        principal = MemberPrincipal(providerUserId = "ghost-user", provider = SocialProvider.KAKAO),
                    )
                }
                exception.errorCode shouldBe ErrorCode.UNAUTHORIZED_ERROR
            }
        }
    },
)
