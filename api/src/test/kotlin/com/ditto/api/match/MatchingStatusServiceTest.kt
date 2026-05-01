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

    "매칭 상태 조회 — 1:1 매칭" - {

        "given: 아무 매칭도 없을 때" - {
            "when: 상태를 조회하면" - {
                "then: personalMatch 가 null 로 반환된다" {
                    val result = matchingStatusService.getMatchingStatus(memberId, quizSetId)

                    result.personalMatch shouldBe null
                    result.quizSetId shouldBe quizSetId
                }
            }
        }

        "given: PENDING 상태의 1:1 매칭이 있을 때" - {
            "when: 상태를 조회하면" - {
                "then: PENDING 매칭이 반환된다" {
                    personalMatchRepository.save(
                        PersonalMatchFixture.create(requesterId = memberId, receiverId = 2L, quizSetId = quizSetId)
                    )

                    val result = matchingStatusService.getMatchingStatus(memberId, quizSetId)

                    result.personalMatch shouldNotBe null
                    result.personalMatch!!.status shouldBe PersonalMatchStatus.PENDING
                }
            }
        }

        "given: PENDING 과 ACCEPTED 매칭이 모두 있을 때" - {
            "when: 상태를 조회하면" - {
                "then: ACCEPTED 매칭이 우선 반환된다" {
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

                    val result = matchingStatusService.getMatchingStatus(memberId, quizSetId)

                    result.personalMatch!!.status shouldBe PersonalMatchStatus.ACCEPTED
                }
            }
        }

        "given: 내가 받은 ACCEPTED 매칭이 있을 때" - {
            "when: 수신자 입장으로 상태를 조회하면" - {
                "then: 해당 ACCEPTED 매칭이 반환된다" {
                    personalMatchRepository.save(
                        PersonalMatchFixture.create(
                            requesterId = 2L, receiverId = memberId, quizSetId = quizSetId,
                            status = PersonalMatchStatus.ACCEPTED,
                        )
                    )

                    val result = matchingStatusService.getMatchingStatus(memberId, quizSetId)

                    result.personalMatch shouldNotBe null
                    result.personalMatch!!.status shouldBe PersonalMatchStatus.ACCEPTED
                    result.personalMatch!!.receiverId shouldBe memberId
                }
            }
        }
    }

    "매칭 상태 조회 — 그룹 매칭" - {

        "given: 그룹 매칭 이력이 없을 때" - {
            "when: 상태를 조회하면" - {
                "then: groupMatchStatus 가 NONE 으로 반환된다" {
                    val result = matchingStatusService.getMatchingStatus(memberId, quizSetId)

                    result.groupMatchStatus shouldBe GroupMatchStatus.NONE
                    result.groupMatchRoomId shouldBe null
                }
            }
        }

        "given: 그룹 매칭을 거절했을 때" - {
            "when: 상태를 조회하면" - {
                "then: groupMatchStatus 가 DECLINED 으로 반환된다" {
                    groupMatchDeclineRepository.save(GroupMatchDecline.of(quizSetId, memberId))

                    val result = matchingStatusService.getMatchingStatus(memberId, quizSetId)

                    result.groupMatchStatus shouldBe GroupMatchStatus.DECLINED
                    result.groupMatchRoomId shouldBe null
                }
            }
        }

        "given: 그룹 방에 참여했을 때" - {
            "when: 상태를 조회하면" - {
                "then: groupMatchStatus 가 JOINED 이고 roomId 가 반환된다" {
                    val room = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = quizSetId))
                    groupMatchMemberRepository.save(GroupMatchMember.of(room.id, memberId))

                    val result = matchingStatusService.getMatchingStatus(memberId, quizSetId)

                    result.groupMatchStatus shouldBe GroupMatchStatus.JOINED
                    result.groupMatchRoomId shouldBe room.id
                }
            }
        }

        "given: 거절 이력과 참여 이력이 모두 있을 때" - {
            "when: 상태를 조회하면" - {
                "then: DECLINED 가 JOINED 보다 우선 반환된다" {
                    val room = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = quizSetId))
                    groupMatchMemberRepository.save(GroupMatchMember.of(room.id, memberId))
                    groupMatchDeclineRepository.save(GroupMatchDecline.of(quizSetId, memberId))

                    val result = matchingStatusService.getMatchingStatus(memberId, quizSetId)

                    result.groupMatchStatus shouldBe GroupMatchStatus.DECLINED
                }
            }
        }
    }
})
