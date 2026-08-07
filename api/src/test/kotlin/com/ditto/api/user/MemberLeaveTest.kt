package com.ditto.api.user

import com.ditto.api.auth.service.AuthService
import com.ditto.api.auth.service.MemberSocialAccountService
import com.ditto.api.support.IntegrationTest
import com.ditto.api.system.ServerTimeProvider
import com.ditto.api.user.dto.LeaveRequest
import com.ditto.api.user.service.LeftMemberPurgeService
import com.ditto.api.user.service.UserService
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.chat.ChatRoomFixture
import com.ditto.domain.chat.ChatRoomMemberFixture
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.refreshtoken.repository.RefreshTokenRepository
import com.ditto.domain.rematch.RematchFixture
import com.ditto.domain.rematch.entity.Rematch
import com.ditto.domain.rematch.entity.RematchCancelReason
import com.ditto.domain.rematch.entity.RematchStatus
import com.ditto.domain.rematch.repository.RematchRepository
import com.ditto.domain.socialaccount.entity.SocialAccount
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.domain.socialaccount.repository.SocialAccountRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

private val SUBMITTED_AT = LocalDateTime.of(2026, 3, 9, 10, 0)

/**
 * 탈퇴 소프트 삭제와 30일 복구. 명세는 탈퇴 화면(피그마 6.2.4)의 안내 문구다.
 */
class MemberLeaveTest(
    private val userService: UserService,
    private val memberSocialAccountService: MemberSocialAccountService,
    private val leftMemberPurgeService: LeftMemberPurgeService,
    private val memberRepository: MemberRepository,
    private val socialAccountRepository: SocialAccountRepository,
    private val personalMatchRepository: PersonalMatchRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val rematchRepository: RematchRepository,
    private val serverTimeProvider: ServerTimeProvider,
    private val authService: AuthService,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun saveActive(nickname: String) =
        memberRepository.save(Member(nickname = nickname).apply { activate() })


    "탈퇴는 데이터를 지우지 않고 상태만 바꾼다" - {
        "탈퇴하면 LEFT가 되고 사유·일시가 남는다" {
            val member = saveActive("탈퇴회원")

            userService.leaveUser(member.id, member.id, LeaveRequest(reason = "not-useful"))

            val left = memberRepository.findById(member.id).orElseThrow()
            left.status shouldBe MemberStatus.LEFT
            left.leaveReason shouldBe "not-useful"
            left.leftAt.shouldNotBeNull()
        }

        "SocialAccount는 보존한다 — 복구·재가입 식별 근거다" {
            val member = saveActive("소셜보존회원")
            socialAccountRepository.save(
                SocialAccount.create(
                    memberId = member.id,
                    provider = SocialProvider.KAKAO,
                    providerUserId = "kakao-preserve-1",
                ),
            )

            userService.leaveUser(member.id, member.id, LeaveRequest(reason = "etc"))

            socialAccountRepository.findByMemberId(member.id).shouldNotBeNull()
        }

        "제재 중에도 탈퇴할 수 있다 — 소프트 삭제는 제재 이력을 보존한다" {
            val member = memberRepository.save(
                Member(nickname = "정지중탈퇴회원").apply {
                    activate()
                    suspendUntil(LocalDateTime.now().plusDays(7))
                },
            )

            userService.leaveUser(member.id, member.id, LeaveRequest(reason = "etc"))

            memberRepository.findById(member.id).orElseThrow().status shouldBe MemberStatus.LEFT
        }

        "남의 계정은 탈퇴시킬 수 없다" {
            val member = saveActive("본인")
            val other = saveActive("타인")

            val exception = shouldThrow<WarnException> {
                userService.leaveUser(other.id, member.id, LeaveRequest())
            }
            exception.errorCode shouldBe ErrorCode.FORBIDDEN
        }
    }

    "탈퇴는 미성사 재매칭 쌍을 취소한다" - {
        /** 한쪽만 선택해 아직 WAITING 인 쌍. 탈퇴 가드는 이 상태를 진행 중으로 보지 않는다. */
        fun saveWaitingRematch(memberId: Long, counterpartId: Long, submittedBy: Long? = null): Rematch {
            val rematch = RematchFixture.create(memberIdA = memberId, memberIdB = counterpartId)
            submittedBy?.let { rematch.submitWants(it, wants = true, now = SUBMITTED_AT) }
            return rematchRepository.save(rematch)
        }

        "미성사 쌍이 CANCELLED(MEMBER_LEFT)로 바뀐다" {
            val member = saveActive("탈퇴예정회원")
            val partner = saveActive("재매칭상대")
            val rematch = saveWaitingRematch(member.id, partner.id, submittedBy = member.id)

            userService.leaveUser(member.id, member.id, LeaveRequest())

            val cancelled = rematchRepository.findById(rematch.id).orElseThrow()
            cancelled.status shouldBe RematchStatus.CANCELLED
            cancelled.cancelReason() shouldBe RematchCancelReason.MEMBER_LEFT
        }

        "취소된 쌍은 남은 상대가 제출해도 성사되지 않는다" {
            val member = saveActive("먼저탈퇴한회원")
            val partner = saveActive("나중제출상대")
            val rematch = saveWaitingRematch(member.id, partner.id, submittedBy = member.id)

            userService.leaveUser(member.id, member.id, LeaveRequest())

            val cancelled = rematchRepository.findById(rematch.id).orElseThrow()
            shouldThrow<WarnException> {
                cancelled.submitWants(partner.id, wants = true, now = SUBMITTED_AT.plusDays(1))
            }.errorCode shouldBe ErrorCode.REMATCH_PAIR_ALREADY_SETTLED
            cancelled.matchedAt() shouldBe null
        }

        // 취소 대상 조회가 WAITING 만 넘기므로 이미 취소된 쌍은 여기 도달하지 않는다.
        // 엔티티 가드 자체는 RematchTest 가 직접 호출해 검증한다.
        "이미 취소된 쌍의 사유는 그대로다" {
            val member = saveActive("거절당한회원")
            val partner = saveActive("거절한상대")
            val rematch = RematchFixture.create(memberIdA = member.id, memberIdB = partner.id)
            rematch.submitWants(member.id, wants = true, now = SUBMITTED_AT)
            rematch.submitWants(partner.id, wants = false, now = SUBMITTED_AT)
            rematchRepository.save(rematch)

            userService.leaveUser(member.id, member.id, LeaveRequest())

            rematchRepository.findById(rematch.id).orElseThrow()
                .cancelReason() shouldBe RematchCancelReason.NOT_MUTUAL
        }

        "다른 두 회원의 쌍은 그대로 둔다" {
            val member = saveActive("탈퇴하는회원")
            val other = saveActive("무관한회원")
            val otherPartner = saveActive("무관한상대")
            val untouched = saveWaitingRematch(other.id, otherPartner.id)

            userService.leaveUser(member.id, member.id, LeaveRequest())

            rematchRepository.findById(untouched.id).orElseThrow().status shouldBe RematchStatus.WAITING
        }
    }

    "진행 중인 매칭·채팅이 있으면 탈퇴가 제한된다" - {
        "수락된 매칭이 있으면 거부한다" {
            val member = saveActive("매칭중회원")
            val partner = saveActive("매칭상대")
            personalMatchRepository.save(
                PersonalMatchFixture.create(
                    requesterId = member.id,
                    receiverId = partner.id,
                    status = PersonalMatchStatus.ACCEPTED,
                ),
            )

            val exception = shouldThrow<WarnException> {
                userService.leaveUser(member.id, member.id, LeaveRequest())
            }
            exception.errorCode shouldBe ErrorCode.CANNOT_LEAVE_WHILE_IN_PROGRESS
        }

        // 방은 스케줄러가 만들어 성사와 예약 사이에 한 주기가 빈다. 그 사이 탈퇴하면 방이 없어
        // 채팅 조건을 빠져나가고, 뒤이은 예약이 탈퇴자와의 방을 만든다.
        "성사됐는데 방이 아직 없으면 거부한다" {
            val member = saveActive("성사된회원")
            val partner = saveActive("성사상대")
            val rematch = RematchFixture.create(memberIdA = member.id, memberIdB = partner.id)
            rematch.submitWants(member.id, wants = true, now = SUBMITTED_AT)
            rematch.submitWants(partner.id, wants = true, now = SUBMITTED_AT)
            rematchRepository.save(rematch)

            val exception = shouldThrow<WarnException> {
                userService.leaveUser(member.id, member.id, LeaveRequest())
            }
            exception.errorCode shouldBe ErrorCode.CANNOT_LEAVE_WHILE_IN_PROGRESS
        }

        // MATCHED 는 종단 상태다. 그것만 보고 막으면 한 번 성사된 회원이 채팅이 끝난 뒤에도
        // 영구히 탈퇴할 수 없다 — 방이 생긴 뒤로는 방 상태가 판정을 이어받아야 한다.
        "성사된 뒤 방이 끝났으면 탈퇴할 수 있다" {
            val member = saveActive("재매칭끝난회원")
            val partner = saveActive("재매칭끝난상대")
            val rematch = RematchFixture.create(memberIdA = member.id, memberIdB = partner.id)
            rematch.submitWants(member.id, wants = true, now = SUBMITTED_AT)
            rematch.submitWants(partner.id, wants = true, now = SUBMITTED_AT)
            val saved = rematchRepository.save(rematch)

            val room = ChatRoomFixture.rematch(sourceId = saved.id).apply {
                expire(ChatRoomFixture.DEFAULT_NOW.plusDays(3))
            }
            chatRoomRepository.save(room)
            chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = room.id, memberId = member.id))

            userService.leaveUser(member.id, member.id, LeaveRequest())

            memberRepository.findById(member.id).orElseThrow().status shouldBe MemberStatus.LEFT
        }

        "끝나지 않은 채팅방이 있으면 거부한다" {
            val member = saveActive("채팅중회원")
            val room = chatRoomRepository.save(ChatRoomFixture.personal(sourceId = 1L))
            chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = room.id, memberId = member.id))

            val exception = shouldThrow<WarnException> {
                userService.leaveUser(member.id, member.id, LeaveRequest())
            }
            exception.errorCode shouldBe ErrorCode.CANNOT_LEAVE_WHILE_IN_PROGRESS
        }

        "종료된 채팅방만 있으면 탈퇴할 수 있다" {
            val member = saveActive("채팅끝난회원")
            val room = ChatRoomFixture.personal(sourceId = 2L).apply {
                expire(ChatRoomFixture.DEFAULT_NOW.plusDays(3))
            }
            chatRoomRepository.save(room)
            chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = room.id, memberId = member.id))

            userService.leaveUser(member.id, member.id, LeaveRequest())

            memberRepository.findById(member.id).orElseThrow().status shouldBe MemberStatus.LEFT
        }

        "거절된 매칭만 있으면 탈퇴할 수 있다" {
            val member = saveActive("거절만있는회원")
            val partner = saveActive("거절한상대")
            personalMatchRepository.save(
                PersonalMatchFixture.create(
                    requesterId = member.id,
                    receiverId = partner.id,
                    status = PersonalMatchStatus.REJECTED,
                ),
            )

            userService.leaveUser(member.id, member.id, LeaveRequest())

            memberRepository.findById(member.id).orElseThrow().status shouldBe MemberStatus.LEFT
        }
    }

    "재가입 시 보존 기간 안이면 복구한다" - {
        "30일 이내 재로그인하면 ACTIVE로 돌아온다" {
            val member = saveActive("복구대상회원")
            socialAccountRepository.save(
                SocialAccount.create(
                    memberId = member.id,
                    provider = SocialProvider.KAKAO,
                    providerUserId = "kakao-restore-1",
                ),
            )
            userService.leaveUser(member.id, member.id, LeaveRequest(reason = "etc"))

            val found = memberSocialAccountService.findOrCreateMember(
                provider = SocialProvider.KAKAO,
                providerUserId = "kakao-restore-1",
                email = null,
                birthDate = null,
            )

            found.id shouldBe member.id
            found.status shouldBe MemberStatus.ACTIVE
            found.leftAt shouldBe null
            found.leaveReason shouldBe null
        }

        "보존 기간이 지났으면 복구하지 않는다" {
            val member = saveActive("기간경과회원")
            socialAccountRepository.save(
                SocialAccount.create(
                    memberId = member.id,
                    provider = SocialProvider.KAKAO,
                    providerUserId = "kakao-expired-1",
                ),
            )
            member.leave(reason = "etc", now = LocalDateTime.now().minusDays(31))
            memberRepository.save(member)

            val found = memberSocialAccountService.findOrCreateMember(
                provider = SocialProvider.KAKAO,
                providerUserId = "kakao-expired-1",
                email = null,
                birthDate = null,
            )

            found.status shouldBe MemberStatus.LEFT
        }
    }

    // 탈퇴는 세션을 즉시 끊으므로 정상 흐름에서는 갱신할 토큰 자체가 없다.
    // 이 게이트는 그 삭제를 지나온 토큰(경합·유실)에 대한 두 번째 방어선이라, 탈퇴 뒤에 발급해 직접 태운다.
    "탈퇴 회원의 토큰은 갱신이 거부된다 (refresh 경로는 JWT 필터를 지나지 않는다)" {
        val member = saveActive("토큰갱신탈퇴회원")
        userService.leaveUser(member.id, member.id, LeaveRequest(reason = "etc"))
        val refreshToken = authService.createRefreshToken(member.id)

        val exception = shouldThrow<WarnException> {
            authService.refresh(refreshToken.token)
        }
        exception.errorCode shouldBe ErrorCode.MEMBER_LEFT
    }

    "삭제 배치는 보존 기간이 지난 회원만 지운다" - {
        "기간 미경과 회원은 건드리지 않는다" {
            val member = saveActive("최근탈퇴회원")
            userService.leaveUser(member.id, member.id, LeaveRequest(reason = "etc"))

            leftMemberPurgeService.purge()

            memberRepository.findById(member.id).isPresent shouldBe true
        }

        "dryRun을 끄면 보존 기간이 지난 회원을 실제로 삭제한다" {
            val member = saveActive("실제삭제대상")
            socialAccountRepository.save(
                SocialAccount.create(
                    memberId = member.id,
                    provider = SocialProvider.KAKAO,
                    providerUserId = "kakao-purge-1",
                ),
            )
            member.leave(reason = "etc", now = LocalDateTime.now().minusDays(31))
            memberRepository.save(member)

            val purgeService = LeftMemberPurgeService(
                memberRepository = memberRepository,
                socialAccountRepository = socialAccountRepository,
                refreshTokenRepository = refreshTokenRepository,
                serverTimeProvider = serverTimeProvider,
                dryRun = false,
                batchLimit = 100,
                retentionDays = 30,
            )

            purgeService.purge() shouldBe 1

            memberRepository.findById(member.id).isPresent shouldBe false
            socialAccountRepository.findByMemberId(member.id) shouldBe null
        }

        "삭제 대상이 없으면 0을 반환한다" {
            leftMemberPurgeService.purge() shouldBe 0
        }

        "스케줄 진입점도 같은 동작을 한다" {
            // @Scheduled 가 호출하는 래퍼 — 대상이 없으면 아무 일도 하지 않는다.
            leftMemberPurgeService.purgeExpired()

            memberRepository.findAll().none { it.isLeft() } shouldBe true
        }

        "dryRun 기본값에서는 삭제하지 않고 대상만 집계한다" {
            val member = saveActive("기간경과탈퇴회원")
            member.leave(reason = "etc", now = LocalDateTime.now().minusDays(31))
            memberRepository.save(member)

            // 테스트 프로필의 dry-run 기본값(true)이라 실제 삭제는 일어나지 않는다.
            leftMemberPurgeService.purge() shouldBe 0
            memberRepository.findById(member.id).isPresent shouldBe true
        }
    }
})
