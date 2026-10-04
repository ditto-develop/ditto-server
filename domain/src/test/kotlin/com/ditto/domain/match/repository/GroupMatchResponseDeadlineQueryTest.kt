package com.ditto.domain.match.repository

import com.ditto.domain.match.GroupMatchFixture
import com.ditto.domain.match.entity.GroupMatchMember
import com.ditto.domain.match.entity.InvitationStatus
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource

class GroupMatchResponseDeadlineQueryTest(
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    private val quizSetRepository: QuizSetRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val thisWeekStartedOn = LocalDate.of(2026, 6, 1)

    fun saveGroup(weekStartedOn: LocalDate = thisWeekStartedOn, acceptedCount: Int = 0): Long {
        val quizSet = quizSetRepository.save(
            QuizSetFixture.create(
                startDate = weekStartedOn.atStartOfDay(),
                endDate = weekStartedOn.plusDays(2).atTime(23, 59, 59),
            ),
        )
        return groupMatchRepository.save(
            GroupMatchFixture.create(quizSetId = quizSet.id, acceptedCount = acceptedCount),
        ).id
    }

    fun saveInvitation(groupMatchId: Long, memberId: Long, accepted: Boolean = false) {
        val invitation = GroupMatchMember.candidate(groupMatchId, memberId)
        if (accepted) invitation.accept()
        groupMatchMemberRepository.save(invitation)
    }

    fun statusOf(groupMatchId: Long, memberId: Long): InvitationStatus? =
        groupMatchMemberRepository.findByRoomIdAndMemberId(groupMatchId, memberId)?.status

    "findGroupMatchIdsByWeekStartedOn" - {

        "그 주의 그룹을 성사 여부와 상관없이 모두 찾고 다른 주는 뺀다" {
            val unformedGroupId = saveGroup()
            val formedGroupId = saveGroup(acceptedCount = 3)
            saveGroup(weekStartedOn = thisWeekStartedOn.plusWeeks(1))
            saveGroup(weekStartedOn = thisWeekStartedOn.minusWeeks(1))

            groupMatchRepository.findGroupMatchIdsByWeekStartedOn(thisWeekStartedOn) shouldContainExactlyInAnyOrder
                listOf(unformedGroupId, formedGroupId)
        }
    }

    "existsAcceptedInUnformedGroupOfWeek" - {

        "그 주 성사 전 그룹에 수락해 둔 초대가 있으면 참이다" {
            val unformedGroupId = saveGroup(acceptedCount = 1)
            saveInvitation(unformedGroupId, memberId = 1L, accepted = true)

            groupMatchMemberRepository.existsAcceptedInUnformedGroupOfWeek(1L, thisWeekStartedOn) shouldBe true
        }

        "대기 초대, 성사된 그룹, 다른 주의 그룹은 보지 않는다" {
            saveInvitation(saveGroup(), memberId = 2L)
            saveInvitation(saveGroup(acceptedCount = 3), memberId = 3L, accepted = true)
            saveInvitation(
                saveGroup(weekStartedOn = thisWeekStartedOn.minusWeeks(1), acceptedCount = 1),
                memberId = 4L,
                accepted = true,
            )

            listOf(2L, 3L, 4L).forEach { memberId ->
                groupMatchMemberRepository.existsAcceptedInUnformedGroupOfWeek(memberId, thisWeekStartedOn) shouldBe false
            }
        }
    }

    "declinePendingInvitations" - {

        "주어진 그룹의 대기 초대만 거절로 바꾸고 바꾼 수를 돌려준다" {
            val targetGroupId = saveGroup()
            val otherGroupId = saveGroup()
            saveInvitation(targetGroupId, 1L)
            saveInvitation(targetGroupId, 2L, accepted = true)
            saveInvitation(otherGroupId, 3L)

            val declinedCount = groupMatchMemberRepository.declinePendingInvitations(
                groupMatchIds = listOf(targetGroupId),
                updatedAt = LocalDateTime.now(),
            )

            declinedCount shouldBe 1L
            statusOf(targetGroupId, 1L) shouldBe InvitationStatus.DECLINED
            statusOf(targetGroupId, 2L) shouldBe InvitationStatus.ACCEPTED
            statusOf(otherGroupId, 3L) shouldBe InvitationStatus.PENDING
        }

        "대기 초대가 없으면 아무것도 바꾸지 않는다" {
            val groupMatchId = saveGroup()
            saveInvitation(groupMatchId, 1L, accepted = true)

            groupMatchMemberRepository.declinePendingInvitations(listOf(groupMatchId), LocalDateTime.now()) shouldBe 0L
            statusOf(groupMatchId, 1L) shouldBe InvitationStatus.ACCEPTED
        }

        "그룹 목록이 비어 있으면 아무것도 바꾸지 않는다" {
            val groupMatchId = saveGroup()
            saveInvitation(groupMatchId, 1L)

            groupMatchMemberRepository.declinePendingInvitations(emptyList(), LocalDateTime.now()) shouldBe 0L
            statusOf(groupMatchId, 1L) shouldBe InvitationStatus.PENDING
        }
    }
})
