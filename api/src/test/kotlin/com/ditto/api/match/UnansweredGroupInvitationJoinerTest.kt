package com.ditto.api.match

import com.ditto.api.match.service.UnansweredGroupInvitationJoiner
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.match.GroupMatchFixture
import com.ditto.domain.match.entity.GroupMatchMember
import com.ditto.domain.match.entity.InvitationStatus
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
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
    dataSource: DataSource,
) : IntegrationTest(
    dataSource,
    {
        // 2026-06-01(월) 시작 주. 마감은 2026-06-05(금) 00:00, 채팅은 2026-06-08(월) 00:00에 끝난다.
        val thisWeekStartedOn = LocalDate.of(2026, 6, 1)
        val deadline = LocalDateTime.of(2026, 6, 5, 0, 0)
        val chatEnd = LocalDateTime.of(2026, 6, 8, 0, 0)

        // 채팅방 기간은 서버 시각으로 잡히므로 스케줄러처럼 넘기는 시각과 서버 시각을 맞춘다.
        fun joinUnansweredAt(now: LocalDateTime) = run {
            serverTimeOverrideRepository.deleteAll()
            serverTimeOverrideRepository.save(
                ServerTimeOverride.disabled().apply { override(now, "관리자", "admin@ditto.pics") },
            )
            unansweredGroupInvitationJoiner.joinUnanswered(now)
        }

        /** 그 주 그룹 하나를 저장하고 회원별 응답 상태를 깐다. 그룹 ID를 돌려준다. */
        fun saveGroupWith(
            statusByMemberId: Map<Long, InvitationStatus>,
            weekStartedOn: LocalDate = thisWeekStartedOn,
        ): Long {
            val quizSet = quizSetRepository.save(
                QuizSetFixture.create(
                    startDate = weekStartedOn.atStartOfDay(),
                    endDate = weekStartedOn.plusDays(2).atTime(23, 59, 59),
                ),
            )
            val acceptedCount = statusByMemberId.values.count { it == InvitationStatus.ACCEPTED }
            val group = groupMatchRepository.save(
                GroupMatchFixture.create(quizSetId = quizSet.id, acceptedCount = acceptedCount),
            )
            statusByMemberId.forEach { (memberId, status) ->
                val member = GroupMatchMember.candidate(roomId = group.id, memberId = memberId)
                when (status) {
                    InvitationStatus.ACCEPTED -> member.accept()
                    InvitationStatus.DECLINED -> member.decline()
                    InvitationStatus.PENDING -> Unit
                }
                groupMatchMemberRepository.save(member)
            }
            return group.id
        }

        fun saveUnansweredGroup(weekStartedOn: LocalDate = thisWeekStartedOn): Long =
            saveGroupWith(
                (1L..3L).associateWith { InvitationStatus.PENDING },
                weekStartedOn = weekStartedOn,
            )

        fun statusOf(groupMatchId: Long, memberId: Long): InvitationStatus? =
            groupMatchMemberRepository.findByRoomIdAndMemberId(groupMatchId, memberId)?.status

        "마감이 지나면 대기 초대만 참여로 바꾸고 새로 성사된 채팅방을 돌려준다" {
            val groupMatchId = saveGroupWith(
                mapOf(
                    1L to InvitationStatus.PENDING,
                    2L to InvitationStatus.ACCEPTED,
                    3L to InvitationStatus.DECLINED,
                    4L to InvitationStatus.PENDING,
                ),
            )

            val formedRooms = joinUnansweredAt(deadline)

            statusOf(groupMatchId, 1L) shouldBe InvitationStatus.ACCEPTED
            statusOf(groupMatchId, 3L) shouldBe InvitationStatus.DECLINED
            statusOf(groupMatchId, 4L) shouldBe InvitationStatus.ACCEPTED
            val chatRoom = chatRoomRepository.findBySourceTypeAndSourceId(ChatRoomType.GROUP, groupMatchId).shouldNotBeNull()
            formedRooms.map { it.id } shouldBe listOf(chatRoom.id)
        }

        "성사되지 않은 그룹은 돌려주지 않는다" {
            val groupMatchId = saveGroupWith(
                mapOf(1L to InvitationStatus.PENDING, 2L to InvitationStatus.DECLINED, 3L to InvitationStatus.DECLINED),
            )

            joinUnansweredAt(deadline).shouldBeEmpty()

            statusOf(groupMatchId, 1L) shouldBe InvitationStatus.ACCEPTED
        }

        "마감 전에는 바꾸지 않는다" {
            val groupMatchId = saveUnansweredGroup()

            joinUnansweredAt(deadline.minusMinutes(1)).shouldBeEmpty()

            statusOf(groupMatchId, 1L) shouldBe InvitationStatus.PENDING
        }

        "주말 동안 스케줄러가 멈췄어도 채팅이 끝나기 전이면 따라잡는다" {
            val groupMatchId = saveUnansweredGroup()

            joinUnansweredAt(chatEnd.minusMinutes(1)).size shouldBe 1

            statusOf(groupMatchId, 1L) shouldBe InvitationStatus.ACCEPTED
        }

        "그 주 채팅이 끝난 뒤에는 바꾸지 않는다" {
            // 지금 시각으로 방을 만들면 다음 주말 방이 된다.
            val groupMatchId = saveUnansweredGroup()

            joinUnansweredAt(chatEnd).shouldBeEmpty()

            statusOf(groupMatchId, 1L) shouldBe InvitationStatus.PENDING
        }

        "서버 시각을 다음 주 금요일로 옮겨도 이번 주 대기 초대는 그대로다" {
            val thisWeekGroupId = saveUnansweredGroup()

            joinUnansweredAt(deadline.plusWeeks(1))

            statusOf(thisWeekGroupId, 1L) shouldBe InvitationStatus.PENDING
        }

        "다음 주 그룹은 이번 주 마감에 바뀌지 않는다" {
            val nextWeekGroupId = saveUnansweredGroup(weekStartedOn = thisWeekStartedOn.plusWeeks(1))

            joinUnansweredAt(deadline)

            statusOf(nextWeekGroupId, 1L) shouldBe InvitationStatus.PENDING
        }

        "다시 돌려도 결과가 같다" {
            val groupMatchId = saveUnansweredGroup()
            joinUnansweredAt(deadline)

            joinUnansweredAt(deadline.plusMinutes(1)).shouldBeEmpty()

            groupMatchRepository.findById(groupMatchId).get().acceptedCount shouldBe 3
        }
    },
)
