package com.ditto.api.match

import com.ditto.api.match.dto.GroupMatchDeclineRequest
import com.ditto.api.match.dto.GroupMatchJoinRequest
import com.ditto.api.match.service.GroupMatchService
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.match.GroupMatchFixture
import com.ditto.domain.match.entity.GroupMatchDecline
import com.ditto.domain.match.repository.GroupMatchDeclineRepository
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import javax.sql.DataSource

class GroupMatchServiceTest(
    private val groupMatchService: GroupMatchService,
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    private val groupMatchDeclineRepository: GroupMatchDeclineRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val memberId = 1L
    val quizSetId = 10L

    "빈 방이 없으면 새 방을 생성하고 참여자 수가 1이 된다" {
        // when
        val result = groupMatchService.joinGroupMatch(memberId, GroupMatchJoinRequest(quizSetId))

        // then
        result.quizSetId shouldBe quizSetId
        result.participantCount shouldBe 1
        result.isActive shouldBe false
        result.roomId shouldNotBe 0L
    }

    "비활성 방이 있으면 기존 방에 배정된다" {
        // given
        val room = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = quizSetId))

        // when
        val result = groupMatchService.joinGroupMatch(memberId, GroupMatchJoinRequest(quizSetId))

        // then
        result.roomId shouldBe room.id
        result.participantCount shouldBe 1
    }

    "3번째 멤버가 참여하면 방이 활성화된다" {
        // given
        groupMatchService.joinGroupMatch(1L, GroupMatchJoinRequest(quizSetId))
        groupMatchService.joinGroupMatch(2L, GroupMatchJoinRequest(quizSetId))

        // when
        val result = groupMatchService.joinGroupMatch(3L, GroupMatchJoinRequest(quizSetId))

        // then
        result.isActive shouldBe true
        result.participantCount shouldBe 3
    }

    "활성화된 방만 있으면 새 방이 생성된다" {
        // given
        val activeRoom = groupMatchRepository.save(
            GroupMatchFixture.create(quizSetId = quizSetId, isActive = true, participantCount = 3)
        )

        // when
        val result = groupMatchService.joinGroupMatch(memberId, GroupMatchJoinRequest(quizSetId))

        // then
        result.roomId shouldNotBe activeRoom.id
        result.isActive shouldBe false
    }

    "이미 참여한 퀴즈셋에 다시 참여하면 ALREADY_JOINED_GROUP 예외가 발생한다" {
        // given
        groupMatchService.joinGroupMatch(memberId, GroupMatchJoinRequest(quizSetId))

        // when & then
        shouldThrow<WarnException> {
            groupMatchService.joinGroupMatch(memberId, GroupMatchJoinRequest(quizSetId))
        }.errorCode shouldBe ErrorCode.ALREADY_JOINED_GROUP
    }

    "거절한 퀴즈셋에 참여하려 하면 ALREADY_DECLINED_GROUP 예외가 발생한다" {
        // given
        groupMatchDeclineRepository.save(GroupMatchDecline.of(quizSetId, memberId))

        // when & then
        shouldThrow<WarnException> {
            groupMatchService.joinGroupMatch(memberId, GroupMatchJoinRequest(quizSetId))
        }.errorCode shouldBe ErrorCode.ALREADY_DECLINED_GROUP
    }

    "그룹 매칭을 거절하면 GroupMatchDecline 레코드가 생성된다" {
        // when
        groupMatchService.declineGroupMatch(memberId, GroupMatchDeclineRequest(quizSetId))

        // then
        groupMatchDeclineRepository.existsByQuizSetIdAndMemberId(quizSetId, memberId) shouldBe true
    }

    "이미 거절한 퀴즈셋을 다시 거절하면 ALREADY_DECLINED_GROUP 예외가 발생한다" {
        // given
        groupMatchDeclineRepository.save(GroupMatchDecline.of(quizSetId, memberId))

        // when & then
        shouldThrow<WarnException> {
            groupMatchService.declineGroupMatch(memberId, GroupMatchDeclineRequest(quizSetId))
        }.errorCode shouldBe ErrorCode.ALREADY_DECLINED_GROUP
    }
})
