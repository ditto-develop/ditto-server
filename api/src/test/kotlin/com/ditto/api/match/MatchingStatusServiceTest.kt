package com.ditto.api.match

import com.ditto.api.match.service.MatchingStatusService
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.match.GroupMatchFixture
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.entity.GroupMatchMember
import com.ditto.domain.match.entity.PersonalMatchStatus
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

    "그룹 초대가 없으면 declined/joined/pending 모두 false 다" {
        val result = matchingStatusService.getMatchingStatus(memberId, quizSetId)

        result.groupDeclined shouldBe false
        result.groupJoined shouldBe false
        result.groupJoinPending shouldBe false
    }

    "받은 초대를 모두 거절하면 groupDeclined=true 다" {
        val room = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = quizSetId))
        val invitation = groupMatchMemberRepository.save(GroupMatchMember.candidate(room.id, memberId))
        invitation.decline()
        groupMatchMemberRepository.save(invitation)

        val result = matchingStatusService.getMatchingStatus(memberId, quizSetId)

        result.groupDeclined shouldBe true
        result.groupJoined shouldBe false
        result.groupJoinPending shouldBe false
    }

    "아직 응답하지 않은 초대가 있으면 셋 다 false 다" {
        val room = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = quizSetId))
        groupMatchMemberRepository.save(GroupMatchMember.candidate(room.id, memberId))

        val result = matchingStatusService.getMatchingStatus(memberId, quizSetId)

        result.groupDeclined shouldBe false
        result.groupJoined shouldBe false
        result.groupJoinPending shouldBe false
    }

    "수락했고 그룹이 성사됐으면 groupJoined=true 다" {
        val room = groupMatchRepository.save(
            GroupMatchFixture.create(quizSetId = quizSetId, acceptedCount = 3),
        )
        val invitation = groupMatchMemberRepository.save(GroupMatchMember.candidate(room.id, memberId))
        invitation.accept()
        groupMatchMemberRepository.save(invitation)

        val result = matchingStatusService.getMatchingStatus(memberId, quizSetId)

        result.groupJoined shouldBe true
        result.groupJoinPending shouldBe false
    }

    "수락했지만 인원이 모자라면 groupJoinPending=true 다" {
        val room = groupMatchRepository.save(
            GroupMatchFixture.create(quizSetId = quizSetId, acceptedCount = 1),
        )
        val invitation = groupMatchMemberRepository.save(GroupMatchMember.candidate(room.id, memberId))
        invitation.accept()
        groupMatchMemberRepository.save(invitation)

        val result = matchingStatusService.getMatchingStatus(memberId, quizSetId)

        result.groupJoinPending shouldBe true
        result.groupJoined shouldBe false
    }

    "한 초대를 거절하고 다른 초대를 수락했으면 수락이 우선이다" {
        val declinedRoom = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = quizSetId))
        val declined = groupMatchMemberRepository.save(GroupMatchMember.candidate(declinedRoom.id, memberId))
        declined.decline()
        groupMatchMemberRepository.save(declined)

        val acceptedRoom = groupMatchRepository.save(
            GroupMatchFixture.create(quizSetId = quizSetId, acceptedCount = 3),
        )
        val accepted = groupMatchMemberRepository.save(GroupMatchMember.candidate(acceptedRoom.id, memberId))
        accepted.accept()
        groupMatchMemberRepository.save(accepted)

        val result = matchingStatusService.getMatchingStatus(memberId, quizSetId)

        result.groupDeclined shouldBe false
        result.groupJoined shouldBe true
    }
})
