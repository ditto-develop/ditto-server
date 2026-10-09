package com.ditto.api.user

import com.ditto.api.auth.service.AuthService
import com.ditto.api.auth.service.MemberSocialAccountService
import com.ditto.api.match.service.PersonalMatchFacade
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
import com.ditto.domain.match.GroupMatchFixture
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.entity.GroupMatchMember
import com.ditto.domain.match.entity.PersonalMatch
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.notification.MemberDeviceFixture
import com.ditto.domain.notification.repository.MemberDeviceRepository
import com.ditto.domain.notification.repository.NotificationRepository
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.refreshtoken.repository.RefreshTokenRepository
import com.ditto.domain.rematch.RematchFixture
import com.ditto.domain.rematch.entity.Rematch
import com.ditto.domain.rematch.entity.RematchCancelReason
import com.ditto.domain.rematch.entity.RematchStatus
import com.ditto.domain.rematch.repository.RematchRepository
import com.ditto.domain.socialaccount.entity.SocialAccount
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.domain.socialaccount.repository.SocialAccountRepository
import com.ditto.domain.system.entity.ServerTimeOverride
import com.ditto.domain.system.repository.ServerTimeOverrideRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

private val SUBMITTED_AT = LocalDateTime.of(2026, 3, 9, 10, 0)
private val GROUP_WEEK_MONDAY = LocalDateTime.of(2026, 4, 6, 0, 0)
private val PERSONAL_WEEK_MONDAY = LocalDateTime.of(2026, 4, 13, 0, 0)
private val GROUP_RESPONSE_DEADLINE = LocalDateTime.of(2026, 4, 10, 0, 0)

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
    private val notificationRepository: NotificationRepository,
    private val memberDeviceRepository: MemberDeviceRepository,
    private val rematchRepository: RematchRepository,
    private val quizSetRepository: QuizSetRepository,
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    private val serverTimeOverrideRepository: ServerTimeOverrideRepository,
    private val serverTimeProvider: ServerTimeProvider,
    private val authService: AuthService,
    private val personalMatchFacade: PersonalMatchFacade,
    transactionManager: PlatformTransactionManager,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun saveActive(nickname: String) =
        memberRepository.save(Member(nickname = nickname).apply { activate() })

    fun saveWaitingRematch(memberId: Long, counterpartId: Long, submittedBy: Long? = null): Rematch {
        val rematch = RematchFixture.create(memberIdA = memberId, memberIdB = counterpartId)
        submittedBy?.let { rematch.submitWants(it, wants = true, now = SUBMITTED_AT) }
        return rematchRepository.save(rematch)
    }

    fun overrideServerTime(at: LocalDateTime) {
        serverTimeOverrideRepository.save(
            ServerTimeOverride.disabled().apply { override(at, "관리자", "admin@ditto.pics") },
        )
    }

    /** 그 주 그룹에 초대된 상태. [accepted]면 수락해 둔다. 수락자 한 명이라 아직 성사 전이다. */
    fun inviteToUnformedGroup(memberId: Long, accepted: Boolean) {
        val quizSet = quizSetRepository.save(
            QuizSetFixture.create(startDate = GROUP_WEEK_MONDAY, matchingType = MatchingType.GROUP),
        )
        val group = groupMatchRepository.save(
            GroupMatchFixture.create(quizSetId = quizSet.id, acceptedCount = if (accepted) 1 else 0),
        )
        val invitation = GroupMatchMember.candidate(roomId = group.id, memberId = memberId)
        if (accepted) {
            invitation.accept()
        }
        groupMatchMemberRepository.save(invitation)
    }

    fun savePersonalMatch(
        requesterId: Long,
        receiverId: Long,
        status: PersonalMatchStatus,
        weekMonday: LocalDateTime = PERSONAL_WEEK_MONDAY,
    ): PersonalMatch {
        val quizSet = quizSetRepository.save(
            QuizSetFixture.create(
                startDate = weekMonday,
                endDate = weekMonday.toLocalDate().plusDays(2).atTime(23, 59, 59),
            ),
        )
        return personalMatchRepository.save(
            PersonalMatchFixture.create(
                requesterId = requesterId,
                receiverId = receiverId,
                quizSetId = quizSet.id,
                status = status,
            ),
        )
    }

    fun saveMatchedRematch(memberId: Long, counterpartId: Long): Rematch {
        val rematch = RematchFixture.create(memberIdA = memberId, memberIdB = counterpartId)
        rematch.submitWants(memberId, wants = true, now = SUBMITTED_AT)
        rematch.submitWants(counterpartId, wants = true, now = SUBMITTED_AT)
        return rematchRepository.save(rematch)
    }


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

    "탈퇴는 세션과 푸시 토큰만은 즉시 지운다" - {
        "푸시 토큰은 지운다 — 탈퇴 뒤 앱이 해제할 수 없다" {
            val member = saveActive("푸시토큰회원")
            val other = saveActive("무관한푸시회원")
            memberDeviceRepository.save(MemberDeviceFixture.create(memberId = member.id, token = "left-phone"))
            memberDeviceRepository.save(MemberDeviceFixture.create(memberId = member.id, token = "left-tablet"))
            memberDeviceRepository.save(MemberDeviceFixture.create(memberId = other.id, token = "other-phone"))

            userService.leaveUser(member.id, member.id, LeaveRequest(reason = "etc"))

            memberDeviceRepository.findAllByMemberId(member.id) shouldBe emptyList()
            memberDeviceRepository.findAllByMemberId(other.id).map { it.token } shouldBe listOf("other-phone")
        }
    }

    "탈퇴는 미성사 재매칭 쌍을 취소한다" - {
        "취소 사유가 MEMBER_LEFT 로 남는다" {
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

        "이미 NOT_MUTUAL 로 취소된 쌍은 건드리지 않는다" {
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
        "이번 주에 응답을 기다리는 1:1 신청이 있으면 보낸 쪽도 받은 쪽도 거부한다" {
            val requester = saveActive("신청보낸회원")
            val receiver = saveActive("신청받은회원")
            savePersonalMatch(requester.id, receiver.id, PersonalMatchStatus.PENDING)
            overrideServerTime(PERSONAL_WEEK_MONDAY.plusDays(1))

            listOf(requester, receiver).forEach { member ->
                shouldThrow<WarnException> {
                    userService.leaveUser(member.id, member.id, LeaveRequest())
                }.errorCode shouldBe ErrorCode.CANNOT_LEAVE_WHILE_IN_PROGRESS
            }
        }

        "지난 주에 응답 없이 남은 1:1 신청은 막지 않는다" {
            val member = saveActive("지난주신청회원")
            val partner = saveActive("응답안한상대")
            savePersonalMatch(
                member.id,
                partner.id,
                PersonalMatchStatus.PENDING,
                weekMonday = PERSONAL_WEEK_MONDAY.minusWeeks(1),
            )
            overrideServerTime(PERSONAL_WEEK_MONDAY.plusDays(1))

            userService.leaveUser(member.id, member.id, LeaveRequest())

            memberRepository.findById(member.id).orElseThrow().status shouldBe MemberStatus.LEFT
        }

        "1:1을 수락하면 그 방이 끝나기 전에는 둘 다 거부한다" {
            val requester = saveActive("수락받은회원")
            val receiver = saveActive("수락한회원")
            val match = savePersonalMatch(requester.id, receiver.id, PersonalMatchStatus.PENDING)
            overrideServerTime(PERSONAL_WEEK_MONDAY.plusDays(1))

            personalMatchFacade.acceptMatch(receiver.id, match.id)

            listOf(requester, receiver).forEach { member ->
                shouldThrow<WarnException> {
                    userService.leaveUser(member.id, member.id, LeaveRequest())
                }.errorCode shouldBe ErrorCode.CANNOT_LEAVE_WHILE_IN_PROGRESS
            }
        }

        "1:1이 성사됐어도 그 방이 끝났으면 탈퇴할 수 있다" {
            val member = saveActive("성사끝난회원")
            val partner = saveActive("성사끝난상대")
            val match = savePersonalMatch(member.id, partner.id, PersonalMatchStatus.ACCEPTED)
            val room = ChatRoomFixture.personal(sourceId = match.id).apply {
                expire(ChatRoomFixture.DEFAULT_NOW.plusDays(3))
            }
            chatRoomRepository.save(room)
            chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = room.id, memberId = member.id))
            overrideServerTime(PERSONAL_WEEK_MONDAY.plusDays(1))

            userService.leaveUser(member.id, member.id, LeaveRequest())

            memberRepository.findById(member.id).orElseThrow().status shouldBe MemberStatus.LEFT
        }

        // 방은 스케줄러가 만들어 성사와 예약 사이에 한 주기가 빈다. 그 사이 탈퇴하면 방이 없어
        // 채팅 조건을 빠져나가고, 뒤이은 예약이 탈퇴자와의 방을 만든다.
        "성사됐는데 방이 아직 없으면 거부한다" {
            val member = saveActive("성사된회원")
            val partner = saveActive("성사상대")
            saveMatchedRematch(member.id, partner.id)

            val exception = shouldThrow<WarnException> {
                userService.leaveUser(member.id, member.id, LeaveRequest())
            }
            exception.errorCode shouldBe ErrorCode.CANNOT_LEAVE_WHILE_IN_PROGRESS
        }

        "성사된 뒤 방이 끝났으면 탈퇴할 수 있다" {
            val member = saveActive("재매칭끝난회원")
            val partner = saveActive("재매칭끝난상대")
            val saved = saveMatchedRematch(member.id, partner.id)

            val room = ChatRoomFixture.rematch(sourceId = saved.id)
            room.expire(ChatRoomFixture.DEFAULT_NOW.plusDays(3))
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

        "성사 전 그룹에 수락해 두었으면 응답 마감 전에는 거부한다" {
            val member = saveActive("그룹수락회원")
            inviteToUnformedGroup(member.id, accepted = true)
            overrideServerTime(GROUP_RESPONSE_DEADLINE.minusMinutes(1))

            val exception = shouldThrow<WarnException> {
                userService.leaveUser(member.id, member.id, LeaveRequest())
            }
            exception.errorCode shouldBe ErrorCode.CANNOT_LEAVE_WHILE_IN_PROGRESS
        }

        "응답 마감이 지나 미성사로 끝난 그룹은 막지 않는다" {
            val member = saveActive("미성사그룹회원")
            inviteToUnformedGroup(member.id, accepted = true)
            overrideServerTime(GROUP_RESPONSE_DEADLINE)

            userService.leaveUser(member.id, member.id, LeaveRequest())

            memberRepository.findById(member.id).orElseThrow().status shouldBe MemberStatus.LEFT
        }

        "그룹 초대에 응답하지 않았으면 막지 않는다" {
            val member = saveActive("그룹대기회원")
            inviteToUnformedGroup(member.id, accepted = false)
            overrideServerTime(GROUP_RESPONSE_DEADLINE.minusMinutes(1))

            userService.leaveUser(member.id, member.id, LeaveRequest())

            memberRepository.findById(member.id).orElseThrow().status shouldBe MemberStatus.LEFT
        }

        "거절된 매칭만 있으면 탈퇴할 수 있다" {
            val member = saveActive("거절만있는회원")
            val partner = saveActive("거절한상대")
            savePersonalMatch(member.id, partner.id, PersonalMatchStatus.REJECTED)
            overrideServerTime(PERSONAL_WEEK_MONDAY.plusDays(1))

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
            // 탈퇴 시점 삭제를 지나온 토큰(레거시 행)도 완전 삭제가 거둔다.
            memberDeviceRepository.save(MemberDeviceFixture.create(memberId = member.id, token = "purge-phone"))

            val purgeService = LeftMemberPurgeService(
                memberRepository = memberRepository,
                socialAccountRepository = socialAccountRepository,
                refreshTokenRepository = refreshTokenRepository,
                notificationRepository = notificationRepository,
                memberDeviceRepository = memberDeviceRepository,
                serverTimeProvider = serverTimeProvider,
                dryRun = false,
                batchLimit = 100,
                retentionDays = 30,
            )

            // 직접 만든 인스턴스라 @Transactional 프록시가 없다. 삭제 쿼리에 필요한 트랜잭션을 여기서 연다.
            TransactionTemplate(transactionManager).execute { purgeService.purge() } shouldBe 1

            memberRepository.findById(member.id).isPresent shouldBe false
            socialAccountRepository.findByMemberId(member.id) shouldBe null
            memberDeviceRepository.findAllByMemberId(member.id) shouldBe emptyList()
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
