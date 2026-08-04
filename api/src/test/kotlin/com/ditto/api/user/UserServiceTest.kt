package com.ditto.api.user

import com.ditto.api.auth.service.AuthService
import com.ditto.api.support.IntegrationTest
import com.ditto.api.user.dto.CreateUserRequest
import com.ditto.api.user.dto.LeaveRequest
import com.ditto.api.user.service.UserService
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.common.exception.WarnException
import com.ditto.domain.intronote.entity.IntroNote
import com.ditto.domain.intronote.entity.IntroQuestion
import com.ditto.domain.intronote.repository.IntroNoteRepository
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.PersonalMatchRepository
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
    caricature = "m1",
)

class UserServiceTest(
    private val userService: UserService,
    private val authService: AuthService,
    private val memberRepository: MemberRepository,
    private val socialAccountRepository: SocialAccountRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val personalMatchRepository: PersonalMatchRepository,
    private val introNoteRepository: IntroNoteRepository,
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
                        caricature = "m1",
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
                result.caricature shouldBe "m1"
                result.joinedAt shouldNotBe null

                val saved = memberRepository.findById(member.id).get()
                saved.status shouldBe MemberStatus.ACTIVE
                saved.interests shouldBe setOf(Interest.TRAVEL, Interest.MUSIC)
                saved.location shouldBe Location.SEOUL
                saved.job shouldBe Job.IT_TECH
                saved.caricature shouldBe "m1"
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
                            caricature = "m1",
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

        "타인 공개 프로필 조회" - {
            fun saveFullMember(nickname: String) = memberRepository.save(
                Member(
                    nickname = nickname,
                    name = "실명비공개",
                    email = "secret@kakao.com",
                    phoneNumber = "010-9999-8888",
                    gender = Gender.FEMALE,
                    age = 27,
                    interests = setOf(Interest.WORKOUT, Interest.TRAVEL, Interest.MUSIC),
                    location = Location.SEOUL,
                    job = Job.DESIGN,
                    caricature = "/assets/avatar/f3.png",
                ).apply { activate() },
            )

            "매칭이 성사된 상대의 프로필을 조회한다 (introduction은 ONE_WORD 소개노트 답변)" {
                val viewer = memberRepository.save(Member(nickname = "조회자").apply { activate() })
                val target = saveFullMember("대상자")
                introNoteRepository.save(IntroNote.create(target.id, IntroQuestion.ONE_WORD, "열정적인사람"))
                personalMatchRepository.save(
                    PersonalMatchFixture.create(
                        requesterId = viewer.id,
                        receiverId = target.id,
                        status = PersonalMatchStatus.ACCEPTED,
                    ),
                )

                val result = userService.getPublicProfile(viewer.id, target.id)

                result.userId shouldBe target.id
                result.nickname shouldBe "대상자"
                result.gender shouldBe "FEMALE"
                result.age shouldBe 27
                result.introduction shouldBe "열정적인사람"
                result.profileImageUrl shouldBe "/assets/avatar/f3.png"
                result.location shouldBe "seoul"
                result.occupation shouldBe "design"
                result.interests.toSet() shouldBe setOf("workout", "travel", "music")
                result.rating shouldBe null
                result.preferredMinAge shouldBe null
                result.preferredMaxAge shouldBe null
            }

            "소개노트 ONE_WORD가 없으면 introduction은 null이다" {
                val viewer = memberRepository.save(Member(nickname = "조회자2").apply { activate() })
                val target = saveFullMember("대상자2")
                personalMatchRepository.save(
                    PersonalMatchFixture.create(
                        requesterId = viewer.id,
                        receiverId = target.id,
                        status = PersonalMatchStatus.ACCEPTED,
                    ),
                )

                val result = userService.getPublicProfile(viewer.id, target.id)

                result.introduction shouldBe null
            }

            "본인 프로필은 매칭 없이도 조회할 수 있다" {
                val me = saveFullMember("본인")

                val result = userService.getPublicProfile(me.id, me.id)

                result.userId shouldBe me.id
            }

            "매칭이 성사되지 않은 상대면 FORBIDDEN 예외가 발생한다" {
                val viewer = memberRepository.save(Member(nickname = "조회자3").apply { activate() })
                val target = saveFullMember("대상자3")

                val exception = shouldThrow<WarnException> {
                    userService.getPublicProfile(viewer.id, target.id)
                }
                exception.errorCode shouldBe ErrorCode.FORBIDDEN
            }

            "본인 프로필 조회 시 회원이 존재하지 않으면 NOT_FOUND 예외가 발생한다" {
                val exception = shouldThrow<WarnException> {
                    userService.getPublicProfile(99999L, 99999L)
                }
                exception.errorCode shouldBe ErrorCode.NOT_FOUND
            }

            "프로필 항목이 비어있으면 null로, 공백 ONE_WORD 답변이면 introduction은 null로 반환한다" {
                val target = memberRepository.save(Member(nickname = "빈프로필").apply { activate() })
                introNoteRepository.save(IntroNote.create(target.id, IntroQuestion.ONE_WORD, "   "))

                val result = userService.getPublicProfile(target.id, target.id)

                result.introduction shouldBe null
                result.gender shouldBe null
                result.age shouldBe null
                result.location shouldBe null
                result.occupation shouldBe null
                result.interests shouldBe emptyList()
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

        "회원 탈퇴 (소프트 삭제)" - {
            "탈퇴 시 회원이 삭제되지 않고 LEFT 상태가 된다" {
                val member = memberRepository.save(Member(nickname = "탈퇴유저").apply { activate() })
                socialAccountRepository.save(SocialAccount.create(member.id, SocialProvider.KAKAO, "leave-user"))
                authService.createRefreshToken(member.id)

                val result = userService.leaveUser(
                    id = member.id,
                    memberId = member.id,
                    request = LeaveRequest(reason = "not-useful"),
                )

                result.id shouldBe member.id
                val left = memberRepository.findById(member.id).orElseThrow()
                left.status shouldBe MemberStatus.LEFT
                left.leaveReason shouldBe "not-useful"
                // 재가입 복구·제재 회피 방지 근거이므로 소셜 계정은 남긴다.
                socialAccountRepository.findByMemberId(member.id) shouldNotBe null
            }

            "탈퇴 시 해당 회원의 모든 리프레시 토큰이 삭제된다" {
                val member = memberRepository.save(Member(nickname = "탈퇴유저").apply { activate() })
                socialAccountRepository.save(SocialAccount.create(member.id, SocialProvider.KAKAO, "leave-user-2"))
                val token1 = authService.createRefreshToken(member.id)
                val token2 = authService.createRefreshToken(member.id)

                userService.leaveUser(
                    id = member.id,
                    memberId = member.id,
                    request = LeaveRequest(),
                )

                refreshTokenRepository.findByToken(token1.token) shouldBe null
                refreshTokenRepository.findByToken(token2.token) shouldBe null
            }

            "존재하지 않는 회원이면 예외가 발생한다" {
                val exception = shouldThrow<WarnException> {
                    userService.leaveUser(
                        id = 99999L,
                        memberId = 1L,
                        request = LeaveRequest(),
                    )
                }
                exception.errorCode shouldBe ErrorCode.NOT_FOUND
            }

            "이용 정지 중인 회원도 탈퇴할 수 있다 — 소프트 삭제는 제재 이력을 보존한다" {
                val member = memberRepository.save(
                    Member(nickname = "정지탈퇴유저").apply {
                        activate()
                        suspendUntil(java.time.LocalDateTime.now().plusDays(7))
                    },
                )

                userService.leaveUser(id = member.id, memberId = member.id, request = LeaveRequest())

                memberRepository.findById(member.id).orElseThrow().status shouldBe MemberStatus.LEFT
            }

            "영구 차단 회원도 탈퇴할 수 있고 재가입 차단 근거(SocialAccount)는 보존된다" {
                val member = memberRepository.save(Member(nickname = "차단탈퇴유저").apply { activate(); ban() })
                socialAccountRepository.save(SocialAccount.create(member.id, SocialProvider.KAKAO, "banned-user"))

                userService.leaveUser(id = member.id, memberId = member.id, request = LeaveRequest())

                memberRepository.findById(member.id).orElseThrow().status shouldBe MemberStatus.LEFT
                socialAccountRepository.findByMemberId(member.id) shouldNotBe null
            }

            "다른 회원의 ID로 탈퇴 요청하면 예외가 발생한다" {
                val memberA = memberRepository.save(Member(nickname = "멤버A"))
                val memberB = memberRepository.save(Member(nickname = "멤버B"))

                val exception = shouldThrow<WarnException> {
                    userService.leaveUser(
                        id = memberB.id,
                        memberId = memberA.id,
                        request = LeaveRequest(),
                    )
                }
                exception.errorCode shouldBe ErrorCode.FORBIDDEN
            }
        }
    },
)
