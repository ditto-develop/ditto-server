package com.ditto.api.match

import com.ditto.api.match.dto.GroupMatchDeclineRequest
import com.ditto.api.match.dto.GroupMatchJoinRequest
import com.ditto.api.match.service.GroupMatchService
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.match.GroupMatchFixture
import com.ditto.domain.match.entity.GroupMatchDecline
import com.ditto.domain.match.entity.GroupMatchMember
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

    "그룹 매칭 참여" - {

        "given: 아무 방도 없을 때" - {
            "when: 첫 번째 멤버가 참여하면" - {
                "then: 새 방이 생성되고 참여자 수가 1이 된다" {
                    val result = groupMatchService.joinGroupMatch(memberId, GroupMatchJoinRequest(quizSetId))

                    result.quizSetId shouldBe quizSetId
                    result.participantCount shouldBe 1
                    result.isActive shouldBe false
                    result.roomId shouldNotBe 0L
                }
            }
        }

        "given: 비활성 방이 이미 존재할 때" - {
            "when: 새 멤버가 참여하면" - {
                "then: 기존 방에 배정된다" {
                    val room = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = quizSetId))

                    val result = groupMatchService.joinGroupMatch(memberId, GroupMatchJoinRequest(quizSetId))

                    result.roomId shouldBe room.id
                    result.participantCount shouldBe 1
                }
            }
        }

        "given: 3명이 같은 방에 참여할 때" - {
            "when: 세 번째 멤버가 참여하면" - {
                "then: 방이 활성화된다 (isActive = true)" {
                    groupMatchService.joinGroupMatch(1L, GroupMatchJoinRequest(quizSetId))
                    groupMatchService.joinGroupMatch(2L, GroupMatchJoinRequest(quizSetId))
                    val result = groupMatchService.joinGroupMatch(3L, GroupMatchJoinRequest(quizSetId))

                    result.isActive shouldBe true
                    result.participantCount shouldBe 3
                }
            }
        }

        "given: 활성화된 방만 있을 때" - {
            "when: 새 멤버가 참여하면" - {
                "then: 새 방이 생성된다" {
                    val activeRoom = groupMatchRepository.save(
                        GroupMatchFixture.create(quizSetId = quizSetId, isActive = true, participantCount = 3)
                    )

                    val result = groupMatchService.joinGroupMatch(memberId, GroupMatchJoinRequest(quizSetId))

                    result.roomId shouldNotBe activeRoom.id
                    result.isActive shouldBe false
                }
            }
        }

        "given: 이미 그룹 매칭에 참여했을 때" - {
            "when: 같은 퀴즈셋으로 다시 참여하면" - {
                "then: ALREADY_JOINED_GROUP 예외가 발생한다" {
                    groupMatchService.joinGroupMatch(memberId, GroupMatchJoinRequest(quizSetId))

                    shouldThrow<WarnException> {
                        groupMatchService.joinGroupMatch(memberId, GroupMatchJoinRequest(quizSetId))
                    }.errorCode shouldBe ErrorCode.ALREADY_JOINED_GROUP
                }
            }
        }

        "given: 그룹 매칭을 거절했을 때" - {
            "when: 다시 참여하려 하면" - {
                "then: ALREADY_DECLINED_GROUP 예외가 발생한다" {
                    groupMatchDeclineRepository.save(GroupMatchDecline.of(quizSetId, memberId))

                    shouldThrow<WarnException> {
                        groupMatchService.joinGroupMatch(memberId, GroupMatchJoinRequest(quizSetId))
                    }.errorCode shouldBe ErrorCode.ALREADY_DECLINED_GROUP
                }
            }
        }
    }

    "그룹 매칭 거절" - {

        "given: 거절 이력이 없을 때" - {
            "when: 거절하면" - {
                "then: GroupMatchDecline 레코드가 생성된다" {
                    groupMatchService.declineGroupMatch(memberId, GroupMatchDeclineRequest(quizSetId))

                    groupMatchDeclineRepository.existsByQuizSetIdAndMemberId(quizSetId, memberId) shouldBe true
                }
            }
        }

        "given: 이미 거절했을 때" - {
            "when: 다시 거절하면" - {
                "then: ALREADY_DECLINED_GROUP 예외가 발생한다" {
                    groupMatchDeclineRepository.save(GroupMatchDecline.of(quizSetId, memberId))

                    shouldThrow<WarnException> {
                        groupMatchService.declineGroupMatch(memberId, GroupMatchDeclineRequest(quizSetId))
                    }.errorCode shouldBe ErrorCode.ALREADY_DECLINED_GROUP
                }
            }
        }
    }
})
