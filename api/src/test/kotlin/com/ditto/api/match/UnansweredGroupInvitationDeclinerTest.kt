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
        val thisWeekStartedOn = LocalDate.of(2026, 6, 1)
        val deadline = LocalDateTime.of(2026, 6, 5, 0, 0)
        val oneMinuteBeforeDeadline = deadline.minusMinutes(1)

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
            val group = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = quizSet.id))
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

        fun statusOf(groupMatchId: Long, memberId: Long): InvitationStatus? =
            groupMatchMemberRepository.findByRoomIdAndMemberId(groupMatchId, memberId)?.status

        "마감이 지나면 대기 초대만 거절로 바뀐다" {
            val groupMatchId = saveGroupWith(
                mapOf(
                    1L to InvitationStatus.PENDING,
                    2L to InvitationStatus.ACCEPTED,
                    3L to InvitationStatus.DECLINED,
                    4L to InvitationStatus.PENDING,
                ),
            )

            val declinedCount = unansweredGroupInvitationDecliner.declineUnanswered(deadline)

            declinedCount shouldBe 2L
            statusOf(groupMatchId, 1L) shouldBe InvitationStatus.DECLINED
            statusOf(groupMatchId, 2L) shouldBe InvitationStatus.ACCEPTED
            statusOf(groupMatchId, 3L) shouldBe InvitationStatus.DECLINED
            statusOf(groupMatchId, 4L) shouldBe InvitationStatus.DECLINED
        }

        "마감 전에는 바꾸지 않는다" {
            val groupMatchId = saveGroupWith(mapOf(1L to InvitationStatus.PENDING))

            unansweredGroupInvitationDecliner.declineUnanswered(oneMinuteBeforeDeadline) shouldBe 0L

            statusOf(groupMatchId, 1L) shouldBe InvitationStatus.PENDING
        }

        "마감 뒤 다음 주 목요일까지는 그 주 대기 초대를 거절로 바꾼다" {
            // 주말 동안 스케줄러가 멈춰도 따라잡는지 본다.
            val groupMatchId = saveGroupWith(mapOf(1L to InvitationStatus.PENDING))

            unansweredGroupInvitationDecliner.declineUnanswered(deadline.plusDays(6)) shouldBe 1L

            statusOf(groupMatchId, 1L) shouldBe InvitationStatus.DECLINED
        }

        "서버 시각을 다음 주 금요일로 옮겨도 이번 주 대기 초대는 그대로다" {
            val thisWeekGroupId = saveGroupWith(mapOf(1L to InvitationStatus.PENDING))

            unansweredGroupInvitationDecliner.declineUnanswered(deadline.plusWeeks(1))

            statusOf(thisWeekGroupId, 1L) shouldBe InvitationStatus.PENDING
        }

        "다음 주 그룹은 이번 주 마감에 거절되지 않는다" {
            val nextWeekGroupId = saveGroupWith(
                mapOf(1L to InvitationStatus.PENDING),
                weekStartedOn = thisWeekStartedOn.plusWeeks(1),
            )

            unansweredGroupInvitationDecliner.declineUnanswered(deadline)

            statusOf(nextWeekGroupId, 1L) shouldBe InvitationStatus.PENDING
        }

        "다시 돌려도 결과가 같다" {
            val groupMatchId = saveGroupWith(mapOf(1L to InvitationStatus.PENDING))

            unansweredGroupInvitationDecliner.declineUnanswered(deadline)
            val declinedCountOnSecondRun = unansweredGroupInvitationDecliner.declineUnanswered(deadline.plusMinutes(1))

            declinedCountOnSecondRun shouldBe 0L
            statusOf(groupMatchId, 1L) shouldBe InvitationStatus.DECLINED
        }
    },
)
