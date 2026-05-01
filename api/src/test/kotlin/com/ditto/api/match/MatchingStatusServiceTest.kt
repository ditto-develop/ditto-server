package com.ditto.api.match

import com.ditto.api.match.dto.GroupMatchStatus
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
import io.kotest.matchers.shouldNotBe
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

    "아무 매칭도 없으면 personalMatch 가 null 로 반환된다" {
        // when
        val result = matchingStatusService.getMatchingStatus(memberId, quizSetId)

        // then
        result.personalMatch shouldBe null
        result.quizSetId shouldBe quizSetId
    }

    "PENDING 1:1 매칭이 있으면 해당 매칭을 반환한다" {
        // given
        personalMatchRepository.save(
            PersonalMatchFixture.create(requesterId = memberId, receiverId = 2L, quizSetId = quizSetId)
        )

        // when
        val result = matchingStatusService.getMatchingStatus(memberId, quizSetId)

        // then
        result.personalMatch shouldNotBe null
        result.personalMatch!!.status shouldBe PersonalMatchStatus.PENDING
    }

    "PENDING 과 ACCEPTED 매칭이 모두 있을 때 ACCEPTED 가 우선 반환된다" {
        // given
        personalMatchRepository.save(
            PersonalMatchFixture.create(
                requesterId = memberId, receiverId = 2L, quizSetId = quizSetId,
                status = PersonalMatchStatus.PENDING,
            )
        )
        personalMatchRepository.save(
            PersonalMatchFixture.create(
                requesterId = memberId, receiverId = 3L, quizSetId = quizSetId,
                status = PersonalMatchStatus.ACCEPTED,
            )
        )

        // when
        val result = matchingStatusService.getMatchingStatus(memberId, quizSetId)

        // then
        result.personalMatch!!.status shouldBe PersonalMatchStatus.ACCEPTED
    }

    "수신자 입장에서도 ACCEPTED 매칭이 반환된다" {
        // given
        personalMatchRepository.save(
            PersonalMatchFixture.create(
                requesterId = 2L, receiverId = memberId, quizSetId = quizSetId,
                status = PersonalMatchStatus.ACCEPTED,
            )
        )

        // when
        val result = matchingStatusService.getMatchingStatus(memberId, quizSetId)

        // then
        result.personalMatch shouldNotBe null
        result.personalMatch!!.status shouldBe PersonalMatchStatus.ACCEPTED
        result.personalMatch!!.receiverId shouldBe memberId
    }

    "그룹 매칭 이력이 없으면 NONE 이 반환된다" {
        // when
        val result = matchingStatusService.getMatchingStatus(memberId, quizSetId)

        // then
        result.groupMatchStatus shouldBe GroupMatchStatus.NONE
        result.groupMatchRoomId shouldBe null
    }

    "그룹 매칭을 거절하면 DECLINED 가 반환된다" {
        // given
        groupMatchDeclineRepository.save(GroupMatchDecline.of(quizSetId, memberId))

        // when
        val result = matchingStatusService.getMatchingStatus(memberId, quizSetId)

        // then
        result.groupMatchStatus shouldBe GroupMatchStatus.DECLINED
        result.groupMatchRoomId shouldBe null
    }

    "그룹 방에 참여하면 JOINED 와 roomId 가 반환된다" {
        // given
        val room = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = quizSetId))
        groupMatchMemberRepository.save(GroupMatchMember.of(room.id, memberId))

        // when
        val result = matchingStatusService.getMatchingStatus(memberId, quizSetId)

        // then
        result.groupMatchStatus shouldBe GroupMatchStatus.JOINED
        result.groupMatchRoomId shouldBe room.id
    }

    "거절 이력과 참여 이력이 모두 있으면 DECLINED 가 우선 반환된다" {
        // given
        val room = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = quizSetId))
        groupMatchMemberRepository.save(GroupMatchMember.of(room.id, memberId))
        groupMatchDeclineRepository.save(GroupMatchDecline.of(quizSetId, memberId))

        // when
        val result = matchingStatusService.getMatchingStatus(memberId, quizSetId)

        // then
        result.groupMatchStatus shouldBe GroupMatchStatus.DECLINED
    }
})
