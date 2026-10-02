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

/** 그룹 응답 마감 처리가 쓰는 조회 — 마감 지난 주의 그룹 ID, 대기 초대 일괄 거절. */
class GroupMatchResponseDeadlineQueryTest(
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    private val quizSetRepository: QuizSetRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val monday = LocalDate.of(2026, 6, 1)

    fun saveGroup(weekStartedOn: LocalDate = monday, acceptedCount: Int = 0): Long {
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

    fun saveInvitation(roomId: Long, memberId: Long, accepted: Boolean = false) {
        val invitation = GroupMatchMember.candidate(roomId, memberId)
        if (accepted) invitation.accept()
        groupMatchMemberRepository.save(invitation)
    }

    "findGroupMatchIdsByWeekStartedOn" - {

        "그 주의 그룹을 성사 여부와 상관없이 모두 찾고 다른 주는 뺀다" {
            val unformed = saveGroup()
            val formed = saveGroup(acceptedCount = 3)
            saveGroup(weekStartedOn = monday.plusWeeks(1))
            saveGroup(weekStartedOn = monday.minusWeeks(1))

            groupMatchRepository.findGroupMatchIdsByWeekStartedOn(monday) shouldContainExactlyInAnyOrder
                listOf(unformed, formed)
        }
    }

    "declinePendingByRoomIdIn" - {

        "주어진 그룹의 대기 초대만 거절로 바꾸고 바꾼 수를 돌려준다" {
            val target = saveGroup()
            val other = saveGroup()
            saveInvitation(target, 1L)
            saveInvitation(target, 2L, accepted = true)
            saveInvitation(other, 3L)

            val declined = groupMatchMemberRepository.declinePendingByRoomIdIn(listOf(target), LocalDateTime.now())

            declined shouldBe 1L
            groupMatchMemberRepository.findByRoomIdAndMemberId(target, 1L)?.status shouldBe InvitationStatus.DECLINED
            groupMatchMemberRepository.findByRoomIdAndMemberId(target, 2L)?.status shouldBe InvitationStatus.ACCEPTED
            groupMatchMemberRepository.findByRoomIdAndMemberId(other, 3L)?.status shouldBe InvitationStatus.PENDING
        }

        "그룹 목록이 비어 있으면 아무것도 바꾸지 않는다" {
            val roomId = saveGroup()
            saveInvitation(roomId, 1L)

            groupMatchMemberRepository.declinePendingByRoomIdIn(emptyList(), LocalDateTime.now()) shouldBe 0L
            groupMatchMemberRepository.findByRoomIdAndMemberId(roomId, 1L)?.status shouldBe InvitationStatus.PENDING
        }
    }
})
