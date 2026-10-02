package com.ditto.api.match

import com.ditto.api.match.service.UnansweredGroupInvitationDecliner
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.match.GroupMatchFixture
import com.ditto.domain.match.entity.GroupMatchMember
import com.ditto.domain.match.entity.InvitationStatus
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.repository.QuizSetRepository
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource

class UnansweredGroupInvitationDeclinerTest(
    private val unansweredGroupInvitationDecliner: UnansweredGroupInvitationDecliner,
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    private val quizSetRepository: QuizSetRepository,
    dataSource: DataSource,
) : IntegrationTest(
    dataSource,
    {
        // 2026-06-01(월) 시작 주. 마감은 2026-06-05(금) 00:00.
        val monday = LocalDate.of(2026, 6, 1)
        val atDeadline = LocalDateTime.of(2026, 6, 5, 0, 0)
        val beforeDeadline = LocalDateTime.of(2026, 6, 4, 23, 59)

        /** 주어진 주의 그룹 하나에 회원별 응답 상태를 깔고 그룹 ID를 돌려준다. */
        fun groupWith(statuses: Map<Long, InvitationStatus>, weekStartedOn: LocalDate = monday): Long {
            val quizSet = quizSetRepository.save(
                QuizSetFixture.create(
                    startDate = weekStartedOn.atStartOfDay(),
                    endDate = weekStartedOn.plusDays(2).atTime(23, 59, 59),
                ),
            )
            val group = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = quizSet.id))
            statuses.forEach { (memberId, status) ->
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

        fun statusOf(roomId: Long, memberId: Long): InvitationStatus? =
            groupMatchMemberRepository.findByRoomIdAndMemberId(roomId, memberId)?.status

        "마감이 지나면 대기 초대만 거절로 바뀐다" {
            val roomId = groupWith(
                mapOf(
                    1L to InvitationStatus.PENDING,
                    2L to InvitationStatus.ACCEPTED,
                    3L to InvitationStatus.DECLINED,
                    4L to InvitationStatus.PENDING,
                ),
            )

            val declined = unansweredGroupInvitationDecliner.declineUnanswered(atDeadline)

            declined shouldBe 2L
            statusOf(roomId, 1L) shouldBe InvitationStatus.DECLINED
            statusOf(roomId, 2L) shouldBe InvitationStatus.ACCEPTED
            statusOf(roomId, 3L) shouldBe InvitationStatus.DECLINED
            statusOf(roomId, 4L) shouldBe InvitationStatus.DECLINED
        }

        "마감 전에는 바꾸지 않는다" {
            val roomId = groupWith(mapOf(1L to InvitationStatus.PENDING))

            unansweredGroupInvitationDecliner.declineUnanswered(beforeDeadline) shouldBe 0L

            statusOf(roomId, 1L) shouldBe InvitationStatus.PENDING
        }

        "마감 다음 주 월~목에도 그 주를 처리한다 — 주말에 스케줄러가 멈춰도 따라잡는다" {
            val roomId = groupWith(mapOf(1L to InvitationStatus.PENDING))

            unansweredGroupInvitationDecliner.declineUnanswered(atDeadline.plusDays(6)) shouldBe 1L

            statusOf(roomId, 1L) shouldBe InvitationStatus.DECLINED
        }

        "서버 시각을 다음 주 금요일로 옮겨도 이번 주 대기 초대는 그대로다" {
            val thisWeekRoomId = groupWith(mapOf(1L to InvitationStatus.PENDING))

            unansweredGroupInvitationDecliner.declineUnanswered(atDeadline.plusWeeks(1))

            statusOf(thisWeekRoomId, 1L) shouldBe InvitationStatus.PENDING
        }

        "다음 주 그룹은 이번 주 마감에 거절되지 않는다" {
            val nextWeekRoomId = groupWith(mapOf(1L to InvitationStatus.PENDING), weekStartedOn = monday.plusWeeks(1))

            unansweredGroupInvitationDecliner.declineUnanswered(atDeadline)

            statusOf(nextWeekRoomId, 1L) shouldBe InvitationStatus.PENDING
        }

        "다시 돌려도 결과가 같다" {
            val roomId = groupWith(mapOf(1L to InvitationStatus.PENDING))

            unansweredGroupInvitationDecliner.declineUnanswered(atDeadline)
            val secondRun = unansweredGroupInvitationDecliner.declineUnanswered(atDeadline.plusMinutes(1))

            secondRun shouldBe 0L
            statusOf(roomId, 1L) shouldBe InvitationStatus.DECLINED
        }
    },
)
