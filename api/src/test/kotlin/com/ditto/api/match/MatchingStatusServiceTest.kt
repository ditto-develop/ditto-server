package com.ditto.api.match

import com.ditto.api.match.service.MatchingStatusService
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.match.GroupMatchFixture
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.entity.GroupMatchDecline
import com.ditto.domain.match.entity.GroupMatchMember
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.GroupMatchDeclineRepository
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import io.kotest.matchers.shouldBe
import javax.sql.DataSource

class MatchingStatusServiceTest(
    private val matchingStatusService: MatchingStatusService,
    private val personalMatchRepository: PersonalMatchRepository,
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    private val groupMatchDeclineRepository: GroupMatchDeclineRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val memberId = 1L
    val quizSetId = 10L

    "아무 매칭도 없으면 빈 목록과 false 플래그를 반환한다" {
        val result = matchingStatusService.getMatchingStatus(memberId, quizSetId)

        result.quizSetId shouldBe quizSetId
        result.sentRequests.size shouldBe 0
        result.receivedRequests.size shouldBe 0
        result.hasAcceptedMatch shouldBe false
        result.acceptedMatchUserId shouldBe null
        result.groupDeclined shouldBe false
        result.groupJoined shouldBe false
        result.groupJoinPending shouldBe false
    }

    "내가 보낸 PENDING 요청은 sentRequests 에 담기고 hasAcceptedMatch 는 false 다" {
        personalMatchRepository.save(
            PersonalMatchFixture.create(requesterId = memberId, receiverId = 2L, quizSetId = quizSetId),
        )

        val result = matchingStatusService.getMatchingStatus(memberId, quizSetId)

        result.sentRequests.size shouldBe 1
        result.sentRequests[0].requesterId shouldBe memberId
        result.sentRequests[0].status shouldBe PersonalMatchStatus.PENDING
        result.hasAcceptedMatch shouldBe false
    }

    "내가 요청자인 ACCEPTED 매칭이 있으면 hasAcceptedMatch=true, 상대 ID를 반환한다" {
        personalMatchRepository.save(
            PersonalMatchFixture.create(
                requesterId = memberId, receiverId = 3L, quizSetId = quizSetId,
                status = PersonalMatchStatus.ACCEPTED,
            ),
        )

        val result = matchingStatusService.getMatchingStatus(memberId, quizSetId)

        result.hasAcceptedMatch shouldBe true
        result.acceptedMatchUserId shouldBe 3L
    }

    "내가 수신자인 ACCEPTED 매칭도 hasAcceptedMatch=true, 요청자 ID를 반환한다" {
        personalMatchRepository.save(
            PersonalMatchFixture.create(
                requesterId = 2L, receiverId = memberId, quizSetId = quizSetId,
                status = PersonalMatchStatus.ACCEPTED,
            ),
        )

        val result = matchingStatusService.getMatchingStatus(memberId, quizSetId)

        result.hasAcceptedMatch shouldBe true
        result.acceptedMatchUserId shouldBe 2L
        result.receivedRequests.size shouldBe 1
        result.receivedRequests[0].requesterId shouldBe 2L
    }

    "그룹 이력이 없으면 declined/joined/pending 모두 false 다" {
        val result = matchingStatusService.getMatchingStatus(memberId, quizSetId)

        result.groupDeclined shouldBe false
        result.groupJoined shouldBe false
        result.groupJoinPending shouldBe false
    }

    "그룹 매칭을 거절하면 groupDeclined=true 다" {
        groupMatchDeclineRepository.save(GroupMatchDecline.of(quizSetId, memberId))

        val result = matchingStatusService.getMatchingStatus(memberId, quizSetId)

        result.groupDeclined shouldBe true
        result.groupJoined shouldBe false
        result.groupJoinPending shouldBe false
    }

    "활성화된 방에 참여하면 groupJoined=true, groupJoinPending=false 다" {
        val room = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = quizSetId, isActive = true))
        groupMatchMemberRepository.save(GroupMatchMember.of(room.id, memberId))

        val result = matchingStatusService.getMatchingStatus(memberId, quizSetId)

        result.groupJoined shouldBe true
        result.groupJoinPending shouldBe false
    }

    "비활성(인원 대기) 방에 참여하면 groupJoinPending=true, groupJoined=false 다" {
        val room = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = quizSetId, isActive = false))
        groupMatchMemberRepository.save(GroupMatchMember.of(room.id, memberId))

        val result = matchingStatusService.getMatchingStatus(memberId, quizSetId)

        result.groupJoinPending shouldBe true
        result.groupJoined shouldBe false
    }

    "거절 이력과 참여 이력이 모두 있으면 groupDeclined 가 우선이다" {
        val room = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = quizSetId, isActive = true))
        groupMatchMemberRepository.save(GroupMatchMember.of(room.id, memberId))
        groupMatchDeclineRepository.save(GroupMatchDecline.of(quizSetId, memberId))

        val result = matchingStatusService.getMatchingStatus(memberId, quizSetId)

        result.groupDeclined shouldBe true
        result.groupJoined shouldBe false
        result.groupJoinPending shouldBe false
    }
})
