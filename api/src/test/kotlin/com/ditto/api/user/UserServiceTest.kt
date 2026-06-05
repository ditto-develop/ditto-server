package com.ditto.api.user

import com.ditto.api.auth.service.AuthService
import com.ditto.api.support.IntegrationTest
import com.ditto.api.user.dto.CreateUserRequest
import com.ditto.api.user.service.UserService
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.common.exception.WarnException
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.Interest
import com.ditto.domain.member.entity.Job
import com.ditto.domain.member.entity.Location
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.refreshtoken.repository.RefreshTokenRepository
import com.ditto.domain.socialaccount.entity.SocialAccount
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.domain.socialaccount.repository.SocialAccountRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource

// 관심사·사는곳·직업이 필수가 된 register 요청을, 검증 대상이 아닌 분기 테스트에서 간편히 만들기 위한 헬퍼.
private fun validRegisterRequest(nickname: String? = null) = CreateUserRequest(
    nickname = nickname,
    interests = setOf("music"),
    location = "seoul",
    job = "it-tech",
)

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

                val result = userService.register(
                    member.id,
                    CreateUserRequest(
                        name = "김철수",
                        nickname = "철수123",
                        phoneNumber = "010-1234-5678",
                        gender = Gender.MALE,
                        age = 25,
                        interests = setOf("travel", "music"),
                        location = "seoul",
                        job = "it-tech",
                    ),
                )

                result.name shouldBe "김철수"
                result.nickname shouldBe "철수123"
                result.phoneNumber shouldBe "010-1234-5678"
                result.gender shouldBe Gender.MALE.name
                result.age shouldBe 25
                result.interests.toSet() shouldBe setOf("travel", "music")
                result.location shouldBe "seoul"
                result.job shouldBe "it-tech"
                result.joinedAt shouldNotBe null

                val saved = memberRepository.findById(member.id).get()
                saved.status shouldBe MemberStatus.ACTIVE
                saved.interests shouldBe setOf(Interest.TRAVEL, Interest.MUSIC)
                saved.location shouldBe Location.SEOUL
                saved.job shouldBe Job.IT_TECH
            }

            "토큰의 회원이 존재하지 않으면 서버 오류가 발생한다" {
                val exception = shouldThrow<ErrorException> {
                    userService.register(
                        99999L,
                        validRegisterRequest(),
                    )
                }
                exception.errorCode shouldBe ErrorCode.INTERNAL_ERROR
            }

            "이미 ACTIVE인 회원이면 예외가 발생한다" {
                val member = memberRepository.save(Member(nickname = "임시닉네임"))
                member.activate()
                memberRepository.save(member)

                val exception = shouldThrow<ErrorException> {
                    userService.register(
                        member.id,
                        validRegisterRequest(),
                    )
                }
                exception.errorCode shouldBe ErrorCode.MEMBER_ALREADY_EXISTS
            }

            "이미 사용 중인 닉네임이면 예외가 발생한다" {
                val existingMember = memberRepository.save(Member(nickname = "중복닉네임"))
                existingMember.activate()
                memberRepository.save(existingMember)

                val pending = memberRepository.save(Member(nickname = "임시닉네임"))

                val exception = shouldThrow<WarnException> {
                    userService.register(
                        pending.id,
                        validRegisterRequest(nickname = "중복닉네임"),
                    )
                }
                exception.errorCode shouldBe ErrorCode.NICKNAME_ALREADY_EXISTS
            }

            "유효하지 않은 code(직업 등)이면 BAD_REQUEST 예외가 발생한다" {
                val pending = memberRepository.save(Member(nickname = "임시닉네임"))

                val exception = shouldThrow<WarnException> {
                    userService.register(
                        pending.id,
                        CreateUserRequest(
                            interests = setOf("travel"),
                            location = "seoul",
                            job = "invalid-code",
                        ),
                    )
                }
                exception.errorCode shouldBe ErrorCode.BAD_REQUEST
            }
        }

        "가입 정보 조회" - {
            "회원의 카카오 수집 정보를 반환한다" {
                val member = memberRepository.save(
                    Member(
                        nickname = "조회유저",
                        email = "user@kakao.com",
                        birthDate = LocalDateTime.of(1995, 3, 15, 0, 0),
                        name = "홍길동",
                        phoneNumber = "010-1234-5678",
                        gender = Gender.MALE,
                    ),
                )

                val result = userService.getMe(member.id)

                result.email shouldBe "user@kakao.com"
                result.birthDate shouldBe LocalDate.of(1995, 3, 15)
                result.name shouldBe "홍길동"
                result.phoneNumber shouldBe "010-1234-5678"
                result.gender shouldBe "MALE"
            }

            "카카오 수집 정보가 없으면 null을 반환한다" {
                val member = memberRepository.save(Member(nickname = "정보없는유저"))

                val result = userService.getMe(member.id)

                result.email shouldBe null
                result.birthDate shouldBe null
                result.name shouldBe null
                result.phoneNumber shouldBe null
                result.gender shouldBe null
            }

            "존재하지 않는 회원이면 예외가 발생한다" {
                val exception = shouldThrow<WarnException> {
                    userService.getMe(99999L)
                }
                exception.errorCode shouldBe ErrorCode.NOT_FOUND
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
                    memberId = member.id,
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
                    memberId = member.id,
                )

                refreshTokenRepository.findByToken(token1.token) shouldBe null
                refreshTokenRepository.findByToken(token2.token) shouldBe null
            }

            "존재하지 않는 회원이면 예외가 발생한다" {
                val exception = shouldThrow<WarnException> {
                    userService.leaveUser(
                        id = 99999L,
                        memberId = 1L,
                    )
                }
                exception.errorCode shouldBe ErrorCode.NOT_FOUND
            }

            "다른 회원의 ID로 탈퇴 요청하면 예외가 발생한다" {
                val memberA = memberRepository.save(Member(nickname = "멤버A"))
                val memberB = memberRepository.save(Member(nickname = "멤버B"))

                val exception = shouldThrow<WarnException> {
                    userService.leaveUser(
                        id = memberB.id,
                        memberId = memberA.id,
                    )
                }
                exception.errorCode shouldBe ErrorCode.FORBIDDEN
            }
        }
    },
)
