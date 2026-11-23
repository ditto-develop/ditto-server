package com.ditto.api.match

import com.ditto.api.match.service.GroupMatchService
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.entity.ChatRoomStatus
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.match.entity.GroupMatch
import com.ditto.domain.match.entity.GroupMatchMember
import com.ditto.domain.match.entity.InvitationStatus
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.notification.repository.NotificationRepository
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.system.OperationWeek
import com.ditto.domain.system.entity.ServerTimeOverride
import com.ditto.domain.system.repository.ServerTimeOverrideRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource

class GroupMatchServiceTest(
    private val groupMatchService: GroupMatchService,
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val quizSetRepository: QuizSetRepository,
    private val serverTimeOverrideRepository: ServerTimeOverrideRepository,
    private val notificationRepository: NotificationRepository,
    private val memberRepository: MemberRepository,
    dataSource: DataSource,
) : IntegrationTest(
    dataSource,
    {

        val thisMonday = OperationWeek.containing(LocalDate.now()).startedOn

        fun overrideServerTime(at: LocalDateTime) {
            serverTimeOverrideRepository.deleteAll()
            serverTimeOverrideRepository.save(
                ServerTimeOverride.disabled().apply { override(at, "관리자", "admin@ditto.pics") },
            )
        }

        // 금요일 이후에 돌려도 응답 마감에 걸리지 않게 서버 시각을 이번 주 수요일로 둔다.
        beforeEach { overrideServerTime(thisMonday.plusDays(2).atTime(12, 0)) }

        // 수락·거절은 이번 주 퀴즈셋만 받는다(MatchWeekPolicy). 테스트마다 DB가 비므로 그때그때 깔고,
        // 같은 테스트 안에서는 이미 깔린 것을 재사용해 "같은 퀴즈셋의 두 그룹"을 만들 수 있게 한다.
        fun thisWeekQuizSetId(): Long {
            val existing = quizSetRepository.findAllByOrderByWeekStartedOnDescIdDesc()
                .firstOrNull { it.weekStartedOn == thisMonday && it.matchingType == MatchingType.GROUP }
            return existing?.id ?: quizSetRepository.save(
                QuizSetFixture.create(
                    startDate = thisMonday.atStartOfDay(),
                    endDate = thisMonday.plusDays(2).atTime(23, 59, 59),
                    matchingType = MatchingType.GROUP,
                ),
            ).id
        }

        fun lastWeekQuizSetId(): Long {
            val monday = thisMonday.minusWeeks(1)
            return quizSetRepository.save(
                QuizSetFixture.create(
                    startDate = monday.atStartOfDay(),
                    endDate = monday.plusDays(2).atTime(23, 59, 59),
                    matchingType = MatchingType.GROUP,
                ),
            ).id
        }

        fun saveCandidateGroup(memberIds: List<Long>, quizSet: Long = thisWeekQuizSetId(), score: Double = 80.0): Long {
            val room = groupMatchRepository.save(GroupMatch.candidate(quizSet, score))
            groupMatchMemberRepository.saveAll(memberIds.map { GroupMatchMember.candidate(room.id, it) })
            return room.id
        }

        fun statusOf(roomId: Long, memberId: Long): InvitationStatus =
            groupMatchMemberRepository.findByRoomIdAndMemberId(roomId, memberId)!!.status

        "수락" - {

            "수락하면 내 상태가 ACCEPTED 가 되고 수락자 수가 늘어난다" {
                val roomId = saveCandidateGroup(listOf(1L, 2L, 3L, 4L))

                val result = groupMatchService.acceptGroupMatch(1L, roomId)

                result.acceptedCount shouldBe 1
                result.isFormed shouldBe false
                statusOf(roomId, 1L) shouldBe InvitationStatus.ACCEPTED
            }

            "후보로 배정되지 않은 그룹은 수락할 수 없다" {
                val roomId = saveCandidateGroup(listOf(2L, 3L, 4L))

                val exception = shouldThrow<WarnException> { groupMatchService.acceptGroupMatch(1L, roomId) }

                exception.errorCode shouldBe ErrorCode.FORBIDDEN
            }

            "없는 그룹은 수락할 수 없다" {
                val exception = shouldThrow<WarnException> { groupMatchService.acceptGroupMatch(1L, 999L) }

                exception.errorCode shouldBe ErrorCode.NOT_FOUND
            }

            "이미 수락한 초대는 다시 수락할 수 없다" {
                val roomId = saveCandidateGroup(listOf(1L, 2L, 3L))
                groupMatchService.acceptGroupMatch(1L, roomId)

                val exception = shouldThrow<WarnException> { groupMatchService.acceptGroupMatch(1L, roomId) }

                exception.errorCode shouldBe ErrorCode.ALREADY_JOINED_GROUP
            }

            "이미 거절한 초대는 수락할 수 없다" {
                val roomId = saveCandidateGroup(listOf(1L, 2L, 3L))
                groupMatchService.declineGroupMatch(1L, roomId)

                val exception = shouldThrow<WarnException> { groupMatchService.acceptGroupMatch(1L, roomId) }

                exception.errorCode shouldBe ErrorCode.ALREADY_DECLINED_GROUP
            }
        }

        "성사" - {

            "수락자가 최소 인원에 닿기 전에는 채팅방이 열리지 않는다" {
                val roomId = saveCandidateGroup(listOf(1L, 2L, 3L, 4L))

                groupMatchService.acceptGroupMatch(1L, roomId)
                groupMatchService.acceptGroupMatch(2L, roomId)

                groupMatchRepository.findById(roomId).get().isActive shouldBe false
                chatRoomRepository.findBySourceTypeAndSourceId(ChatRoomType.GROUP, roomId) shouldBe null
            }

            "수락자가 최소 인원에 닿으면 성사되고 채팅방이 열린다" {
                val roomId = saveCandidateGroup(listOf(1L, 2L, 3L, 4L))

                groupMatchService.acceptGroupMatch(1L, roomId)
                groupMatchService.acceptGroupMatch(2L, roomId)
                val result = groupMatchService.acceptGroupMatch(3L, roomId)

                result.isFormed shouldBe true
                result.acceptedCount shouldBe 3
                groupMatchRepository.findById(roomId).get().isActive shouldBe true
                chatRoomRepository.findBySourceTypeAndSourceId(ChatRoomType.GROUP, roomId)
                    ?.sourceId shouldBe roomId
            }

            "성사 뒤에 수락한 사람도 채팅방에 들어간다" {
                // 그룹 인원(4)이 성사 최소 인원(3)보다 커서 4번째 수락이 정상 경로다.
                val roomId = saveCandidateGroup(listOf(1L, 2L, 3L, 4L))
                groupMatchService.acceptGroupMatch(1L, roomId)
                groupMatchService.acceptGroupMatch(2L, roomId)
                groupMatchService.acceptGroupMatch(3L, roomId)

                val result = groupMatchService.acceptGroupMatch(4L, roomId)

                result.acceptedCount shouldBe 4
                result.isFormed shouldBe true
                val chatRoom = chatRoomRepository.findBySourceTypeAndSourceId(ChatRoomType.GROUP, roomId)!!
                chatRoomMemberRepository.findByRoomIdIn(listOf(chatRoom.id))
                    .map { it.memberId } shouldContainExactlyInAnyOrder listOf(1L, 2L, 3L, 4L)
            }

            "채팅방에는 수락한 사람만 들어간다" {
                val roomId = saveCandidateGroup(listOf(1L, 2L, 3L, 4L))
                groupMatchService.declineGroupMatch(4L, roomId)

                groupMatchService.acceptGroupMatch(1L, roomId)
                groupMatchService.acceptGroupMatch(2L, roomId)
                groupMatchService.acceptGroupMatch(3L, roomId)

                val chatRoom = chatRoomRepository.findBySourceTypeAndSourceId(ChatRoomType.GROUP, roomId)!!
                chatRoomMemberRepository.findByRoomIdIn(listOf(chatRoom.id))
                    .map { it.memberId } shouldContainExactlyInAnyOrder listOf(1L, 2L, 3L)
            }
        }

        "자동 거절" - {

            "한 그룹을 수락하면 같은 퀴즈셋의 남은 초대는 자동 거절된다" {
                val acceptedRoomId = saveCandidateGroup(listOf(1L, 2L, 3L))
                val otherRoomId = saveCandidateGroup(listOf(1L, 4L, 5L))

                groupMatchService.acceptGroupMatch(1L, acceptedRoomId)

                statusOf(otherRoomId, 1L) shouldBe InvitationStatus.DECLINED
                // 그 그룹의 다른 구성원은 그대로 대기한다 — 성사 가능성만 낮아진다
                statusOf(otherRoomId, 4L) shouldBe InvitationStatus.PENDING
            }

            "다른 퀴즈셋의 초대는 건드리지 않는다" {
                val acceptedRoomId = saveCandidateGroup(listOf(1L, 2L, 3L))
                val otherQuizSetRoomId = saveCandidateGroup(listOf(1L, 4L, 5L), quizSet = 99L)

                groupMatchService.acceptGroupMatch(1L, acceptedRoomId)

                statusOf(otherQuizSetRoomId, 1L) shouldBe InvitationStatus.PENDING
            }
        }

        "거절" - {

            "거절하면 내 상태가 DECLINED 가 된다" {
                val roomId = saveCandidateGroup(listOf(1L, 2L, 3L))

                groupMatchService.declineGroupMatch(1L, roomId)

                statusOf(roomId, 1L) shouldBe InvitationStatus.DECLINED
                groupMatchRepository.findById(roomId).get().acceptedCount shouldBe 0
            }

            "이미 응답한 초대는 거절할 수 없다" {
                val roomId = saveCandidateGroup(listOf(1L, 2L, 3L))
                groupMatchService.declineGroupMatch(1L, roomId)

                val exception = shouldThrow<WarnException> { groupMatchService.declineGroupMatch(1L, roomId) }

                exception.errorCode shouldBe ErrorCode.ALREADY_DECLINED_GROUP
            }
        }

        "지난 주 후보" - {

            "지난 주 후보 그룹은 수락할 수 없다 — 지난 사이클 그룹이 오늘 성사되면 안 된다" {
                val roomId = saveCandidateGroup(listOf(1L, 2L, 3L), quizSet = lastWeekQuizSetId())

                val exception = shouldThrow<WarnException> { groupMatchService.acceptGroupMatch(1L, roomId) }

                exception.errorCode shouldBe ErrorCode.NOT_MATCHING_PERIOD
                statusOf(roomId, 1L) shouldBe InvitationStatus.PENDING
                chatRoomRepository.findBySourceTypeAndSourceId(ChatRoomType.GROUP, roomId) shouldBe null
            }

            "지난 주 후보 그룹은 거절할 수도 없다" {
                val roomId = saveCandidateGroup(listOf(1L, 2L, 3L), quizSet = lastWeekQuizSetId())

                val exception = shouldThrow<WarnException> { groupMatchService.declineGroupMatch(1L, roomId) }

                exception.errorCode shouldBe ErrorCode.NOT_MATCHING_PERIOD
            }
        }

        "응답 마감 (그 주 금요일 00:00)" - {
            val deadline = thisMonday.plusDays(4).atStartOfDay()
            val oneMinuteBeforeDeadline = deadline.minusMinutes(1)

            "마감 직전에는 수락할 수 있다" {
                val roomId = saveCandidateGroup(listOf(1L, 2L, 3L))
                overrideServerTime(oneMinuteBeforeDeadline)

                groupMatchService.acceptGroupMatch(1L, roomId)

                statusOf(roomId, 1L) shouldBe InvitationStatus.ACCEPTED
            }

            "마감이 지나면 수락할 수 없고 초대는 그대로다" {
                val roomId = saveCandidateGroup(listOf(1L, 2L, 3L))
                overrideServerTime(deadline)

                val exception = shouldThrow<WarnException> { groupMatchService.acceptGroupMatch(1L, roomId) }

                exception.errorCode shouldBe ErrorCode.NOT_MATCHING_PERIOD
                statusOf(roomId, 1L) shouldBe InvitationStatus.PENDING
            }

            "마감이 지나면 거절도 받지 않는다" {
                val roomId = saveCandidateGroup(listOf(1L, 2L, 3L))
                overrideServerTime(deadline)

                val exception = shouldThrow<WarnException> { groupMatchService.declineGroupMatch(1L, roomId) }

                exception.errorCode shouldBe ErrorCode.NOT_MATCHING_PERIOD
            }
        }

        "미응답 자동 참여" - {
            val deadline = thisMonday.plusDays(4).atStartOfDay()
            var memberSequence = 0

            fun saveMember(status: MemberStatus = MemberStatus.ACTIVE, suspendedUntil: LocalDateTime? = null): Long {
                memberSequence++
                return memberRepository.save(
                    MemberFixture.create(
                        nickname = "회원$memberSequence",
                        email = "member$memberSequence@example.com",
                        status = status,
                        suspendedUntil = suspendedUntil,
                    ),
                ).id
            }

            fun saveActiveMembers(count: Int): List<Long> = List(count) { saveMember() }

            fun groupChatRoomOf(groupMatchId: Long): ChatRoom =
                chatRoomRepository.findBySourceTypeAndSourceId(ChatRoomType.GROUP, groupMatchId).shouldNotBeNull()

            fun chatMemberIdsOfGroup(groupMatchId: Long): List<Long> =
                chatRoomMemberRepository.findByRoomIdIn(listOf(groupChatRoomOf(groupMatchId).id)).map { it.memberId }

            // 서버 시각이 수요일이라 성사된 방은 금요일 개방을 기다린다.
            fun formGroupBeforeDeadline(memberIds: List<Long>): Long {
                val groupMatchId = saveCandidateGroup(memberIds)
                memberIds.take(3).forEach { groupMatchService.acceptGroupMatch(it, groupMatchId) }
                return groupMatchId
            }

            "거절하지 않은 사람이 3명 이상이면 미응답자를 참여로 바꿔 성사시키고 바로 열린 채팅방을 만든다" {
                val (accepted, first, second, declined) = saveActiveMembers(4)
                val groupMatchId = saveCandidateGroup(listOf(accepted, first, second, declined))
                groupMatchService.acceptGroupMatch(accepted, groupMatchId)
                groupMatchService.declineGroupMatch(declined, groupMatchId)
                overrideServerTime(deadline)

                val chatRoomId = groupMatchService.joinUnansweredInvitations(groupMatchId, deadline)

                statusOf(groupMatchId, first) shouldBe InvitationStatus.ACCEPTED
                statusOf(groupMatchId, second) shouldBe InvitationStatus.ACCEPTED
                statusOf(groupMatchId, declined) shouldBe InvitationStatus.DECLINED
                val group = groupMatchRepository.findById(groupMatchId).get()
                group.isActive shouldBe true
                group.acceptedCount shouldBe 3
                val chatRoom = groupChatRoomOf(groupMatchId)
                chatRoomId shouldBe chatRoom.id
                chatRoom.status shouldBe ChatRoomStatus.ACTIVE
                chatMemberIdsOfGroup(groupMatchId) shouldContainExactlyInAnyOrder listOf(accepted, first, second)
            }

            "거절하지 않은 사람이 3명 미만이면 참여로만 바꾸고 성사시키지 않는다" {
                val (first, second, declined) = saveActiveMembers(3)
                val groupMatchId = saveCandidateGroup(listOf(first, second, declined))
                groupMatchService.declineGroupMatch(declined, groupMatchId)
                overrideServerTime(deadline)

                val chatRoomId = groupMatchService.joinUnansweredInvitations(groupMatchId, deadline)

                chatRoomId shouldBe null
                statusOf(groupMatchId, first) shouldBe InvitationStatus.ACCEPTED
                statusOf(groupMatchId, second) shouldBe InvitationStatus.ACCEPTED
                groupMatchRepository.findById(groupMatchId).get().isActive shouldBe false
                chatRoomRepository.findBySourceTypeAndSourceId(ChatRoomType.GROUP, groupMatchId) shouldBe null
            }

            "활동 중이 아닌 회원의 초대는 거절로 바꾸고 인원에 넣지 않는다" {
                val (first, second) = saveActiveMembers(2)
                val left = saveMember(MemberStatus.LEFT)
                val suspended = saveMember(MemberStatus.SUSPENDED, suspendedUntil = deadline.plusDays(1))
                val groupMatchId = saveCandidateGroup(listOf(first, second, left, suspended))
                overrideServerTime(deadline)

                groupMatchService.joinUnansweredInvitations(groupMatchId, deadline) shouldBe null

                statusOf(groupMatchId, first) shouldBe InvitationStatus.ACCEPTED
                statusOf(groupMatchId, left) shouldBe InvitationStatus.DECLINED
                statusOf(groupMatchId, suspended) shouldBe InvitationStatus.DECLINED
                val group = groupMatchRepository.findById(groupMatchId).get()
                group.acceptedCount shouldBe 2
                group.isActive shouldBe false
            }

            "정지 기간이 끝났는데 상태가 아직 정지인 회원은 참여로 바꾼다" {
                val (first, second) = saveActiveMembers(2)
                val suspensionOver = saveMember(MemberStatus.SUSPENDED, suspendedUntil = deadline.minusHours(1))
                val groupMatchId = saveCandidateGroup(listOf(first, second, suspensionOver))
                overrideServerTime(deadline)

                groupMatchService.joinUnansweredInvitations(groupMatchId, deadline)

                statusOf(groupMatchId, suspensionOver) shouldBe InvitationStatus.ACCEPTED
                groupMatchRepository.findById(groupMatchId).get().isActive shouldBe true
            }

            "성사돼 개방을 기다리는 방이면 미응답자를 그 방에 넣고 방은 돌려주지 않는다" {
                val memberIds = saveActiveMembers(4)
                val groupMatchId = formGroupBeforeDeadline(memberIds)
                overrideServerTime(deadline)

                val chatRoomId = groupMatchService.joinUnansweredInvitations(groupMatchId, deadline)

                chatRoomId shouldBe null
                statusOf(groupMatchId, memberIds[3]) shouldBe InvitationStatus.ACCEPTED
                groupMatchRepository.findById(groupMatchId).get().acceptedCount shouldBe 4
                chatMemberIdsOfGroup(groupMatchId) shouldContainExactlyInAnyOrder memberIds
            }

            "이미 열린 방이면 미응답자를 넣고 오픈 알림을 받도록 그 방을 돌려준다" {
                val memberIds = saveActiveMembers(4)
                val groupMatchId = formGroupBeforeDeadline(memberIds)
                chatRoomRepository.save(groupChatRoomOf(groupMatchId).apply { open(deadline) })
                overrideServerTime(deadline)

                val chatRoomId = groupMatchService.joinUnansweredInvitations(groupMatchId, deadline)

                chatRoomId shouldBe groupChatRoomOf(groupMatchId).id
                chatMemberIdsOfGroup(groupMatchId) shouldContainExactlyInAnyOrder memberIds
            }

            "성사됐다가 이미 끝난 방이면 미응답자를 거절로 바꾸고 방에 넣지 않는다" {
                val memberIds = saveActiveMembers(4)
                val groupMatchId = formGroupBeforeDeadline(memberIds)
                chatRoomRepository.save(groupChatRoomOf(groupMatchId).apply { endByInsufficientMembers(deadline) })
                overrideServerTime(deadline)

                groupMatchService.joinUnansweredInvitations(groupMatchId, deadline) shouldBe null

                statusOf(groupMatchId, memberIds[3]) shouldBe InvitationStatus.DECLINED
                chatMemberIdsOfGroup(groupMatchId) shouldContainExactlyInAnyOrder memberIds.take(3)
            }

            "자동 참여로 성사돼도 성사 알림은 보내지 않는다" {
                val memberIds = saveActiveMembers(3)
                val groupMatchId = saveCandidateGroup(memberIds)
                overrideServerTime(deadline)

                groupMatchService.joinUnansweredInvitations(groupMatchId, deadline)

                notificationRepository.findByMemberIdIn(memberIds)
                    .filter { it.type == NotificationType.GROUP_FORMED } shouldBe emptyList()
            }

            "다시 불러도 결과가 같다" {
                val groupMatchId = saveCandidateGroup(saveActiveMembers(3))
                overrideServerTime(deadline)
                groupMatchService.joinUnansweredInvitations(groupMatchId, deadline)

                val chatRoomIdOnSecondRun = groupMatchService.joinUnansweredInvitations(groupMatchId, deadline)

                chatRoomIdOnSecondRun shouldBe null
                groupMatchRepository.findById(groupMatchId).get().acceptedCount shouldBe 3
            }
        }
    },
)
