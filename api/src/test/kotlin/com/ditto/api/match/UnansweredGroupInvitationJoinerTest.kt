package com.ditto.api.match

import com.ditto.api.match.service.UnansweredGroupInvitationJoiner
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.match.GroupMatchFixture
import com.ditto.domain.match.entity.GroupMatchMember
import com.ditto.domain.match.entity.InvitationStatus
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.system.entity.ServerTimeOverride
import com.ditto.domain.system.repository.ServerTimeOverrideRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource

class UnansweredGroupInvitationJoinerTest(
    private val unansweredGroupInvitationJoiner: UnansweredGroupInvitationJoiner,
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val quizSetRepository: QuizSetRepository,
    private val serverTimeOverrideRepository: ServerTimeOverrideRepository,
    private val memberRepository: MemberRepository,
    dataSource: DataSource,
) : IntegrationTest(
    dataSource,
    {
        // 2026-06-01(월) 시작 주. 마감은 2026-06-05(금) 00:00, 채팅은 2026-06-08(월) 00:00에 끝난다.
        val thisWeekStartedOn = LocalDate.of(2026, 6, 1)
        val deadline = LocalDateTime.of(2026, 6, 5, 0, 0)
        val chatEnd = LocalDateTime.of(2026, 6, 8, 0, 0)

        var memberSequence = 0

        // 채팅방 기간은 서버 시각으로 잡히므로 스케줄러처럼 넘기는 시각과 서버 시각을 맞춘다.
        fun joinUnansweredAt(now: LocalDateTime): List<ChatRoom> {
            serverTimeOverrideRepository.deleteAll()
            serverTimeOverrideRepository.save(
                ServerTimeOverride.disabled().apply { override(now, "관리자", "admin@ditto.pics") },
            )
            return unansweredGroupInvitationJoiner.joinUnanswered(now)
        }

        fun saveActiveMember(): Long {
            memberSequence++
            return memberRepository.save(
                MemberFixture.create(
                    nickname = "회원$memberSequence",
                    email = "member$memberSequence@example.com",
                    status = MemberStatus.ACTIVE,
                ),
            ).id
        }

        /** 그 주 그룹 하나를 저장하고 구성원마다 [statuses] 순서대로 응답 상태를 깐다. 구성원 ID 를 같은 순서로 돌려준다. */
        fun saveGroupWith(
            statuses: List<InvitationStatus>,
            weekStartedOn: LocalDate = thisWeekStartedOn,
        ): Pair<Long, List<Long>> {
            val quizSet = quizSetRepository.save(
                QuizSetFixture.create(
                    startDate = weekStartedOn.atStartOfDay(),
                    endDate = weekStartedOn.plusDays(2).atTime(23, 59, 59),
                ),
            )
            val group = groupMatchRepository.save(
                GroupMatchFixture.create(
                    quizSetId = quizSet.id,
                    acceptedCount = statuses.count { it == InvitationStatus.ACCEPTED },
                ),
            )
            val memberIds = statuses.map { status ->
                val invitation = GroupMatchMember.candidate(roomId = group.id, memberId = saveActiveMember())
                when (status) {
                    InvitationStatus.ACCEPTED -> invitation.accept()
                    InvitationStatus.DECLINED -> invitation.decline()
                    InvitationStatus.PENDING -> Unit
                }
                groupMatchMemberRepository.save(invitation).memberId
            }
            return group.id to memberIds
        }

        fun saveUnansweredGroup(weekStartedOn: LocalDate = thisWeekStartedOn): Pair<Long, List<Long>> =
            saveGroupWith(List(3) { InvitationStatus.PENDING }, weekStartedOn)

        fun statusOf(groupMatchId: Long, memberId: Long): InvitationStatus? =
            groupMatchMemberRepository.findByRoomIdAndMemberId(groupMatchId, memberId)?.status

        "마감이 지나면 대기 초대만 참여로 바꾸고 새로 성사된 채팅방을 돌려준다" {
            val (groupMatchId, memberIds) = saveGroupWith(
                listOf(
                    InvitationStatus.PENDING,
                    InvitationStatus.ACCEPTED,
                    InvitationStatus.DECLINED,
                    InvitationStatus.PENDING,
                ),
            )
            val (unansweredFirst, _, declined, unansweredSecond) = memberIds

            val openedRooms = joinUnansweredAt(deadline)

            statusOf(groupMatchId, unansweredFirst) shouldBe InvitationStatus.ACCEPTED
            statusOf(groupMatchId, declined) shouldBe InvitationStatus.DECLINED
            statusOf(groupMatchId, unansweredSecond) shouldBe InvitationStatus.ACCEPTED
            val chatRoom = chatRoomRepository.findBySourceTypeAndSourceId(ChatRoomType.GROUP, groupMatchId).shouldNotBeNull()
            openedRooms.map { it.id } shouldBe listOf(chatRoom.id)
        }

        "인원이 모자란 그룹은 참여로만 바꾸고 돌려주지 않는다" {
            val (groupMatchId, memberIds) = saveGroupWith(
                listOf(InvitationStatus.PENDING, InvitationStatus.DECLINED, InvitationStatus.DECLINED),
            )

            joinUnansweredAt(deadline).shouldBeEmpty()

            statusOf(groupMatchId, memberIds.first()) shouldBe InvitationStatus.ACCEPTED
        }

        "마감 전에는 바꾸지 않는다" {
            val (groupMatchId, memberIds) = saveUnansweredGroup()

            joinUnansweredAt(deadline.minusMinutes(1)).shouldBeEmpty()

            statusOf(groupMatchId, memberIds.first()) shouldBe InvitationStatus.PENDING
        }

        "주말 동안 스케줄러가 멈췄어도 채팅이 끝나기 전이면 따라잡는다" {
            val (groupMatchId, memberIds) = saveUnansweredGroup()

            joinUnansweredAt(chatEnd.minusMinutes(1)).size shouldBe 1

            statusOf(groupMatchId, memberIds.first()) shouldBe InvitationStatus.ACCEPTED
        }

        "그 주 채팅이 끝난 뒤에는 바꾸지 않는다" {
            val (groupMatchId, memberIds) = saveUnansweredGroup()

            joinUnansweredAt(chatEnd).shouldBeEmpty()

            statusOf(groupMatchId, memberIds.first()) shouldBe InvitationStatus.PENDING
        }

        "서버 시각을 다음 주 금요일로 옮겨도 이번 주 대기 초대는 그대로다" {
            val (thisWeekGroupId, memberIds) = saveUnansweredGroup()

            joinUnansweredAt(deadline.plusWeeks(1))

            statusOf(thisWeekGroupId, memberIds.first()) shouldBe InvitationStatus.PENDING
        }

        "다음 주 그룹은 이번 주 마감에 바뀌지 않는다" {
            val (nextWeekGroupId, memberIds) = saveUnansweredGroup(weekStartedOn = thisWeekStartedOn.plusWeeks(1))

            joinUnansweredAt(deadline)

            statusOf(nextWeekGroupId, memberIds.first()) shouldBe InvitationStatus.PENDING
        }

        "다시 돌려도 결과가 같다" {
            val (groupMatchId, _) = saveUnansweredGroup()
            joinUnansweredAt(deadline)

            joinUnansweredAt(deadline.plusMinutes(1)).shouldBeEmpty()

            groupMatchRepository.findById(groupMatchId).get().acceptedCount shouldBe 3
        }
    },
)
