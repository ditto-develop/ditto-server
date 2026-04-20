package com.ditto.domain.match.entity

import com.ditto.domain.match.GroupMatchRoomFixture
import com.ditto.domain.match.repository.GroupMatchDeclineRepository
import com.ditto.domain.match.repository.GroupMatchRoomMemberRepository
import com.ditto.domain.match.repository.GroupMatchRoomRepository
import com.ditto.domain.support.IntegrationTest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import javax.sql.DataSource

class GroupMatchRoomTest(
    private val groupMatchRoomRepository: GroupMatchRoomRepository,
    private val groupMatchRoomMemberRepository: GroupMatchRoomMemberRepository,
    private val groupMatchDeclineRepository: GroupMatchDeclineRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "GroupMatchRoom 생성" - {
        "given: quizSetId=1 인 방을 생성할 때" - {
            "when: 저장하면" - {
                "then: isActive=false, participantCount=0 으로 초기화된다" {
                    val room = groupMatchRoomRepository.save(GroupMatchRoomFixture.create(quizSetId = 1L))

                    room.id shouldNotBe 0L
                    room.quizSetId shouldBe 1L
                    room.isActive shouldBe false
                    room.participantCount shouldBe 0
                }
            }
        }

        "given: quizSetId=1 인 방이 이미 있을 때" - {
            "when: 동일 quizSetId 로 방을 하나 더 생성하면" - {
                "then: 퀴즈셋당 방 개수 제한이 없으므로 정상 저장된다" {
                    val room1 = groupMatchRoomRepository.save(GroupMatchRoomFixture.create(quizSetId = 1L))
                    val room2 = groupMatchRoomRepository.save(GroupMatchRoomFixture.create(quizSetId = 1L))

                    room1.id shouldNotBe room2.id
                }
            }
        }
    }

    "GroupMatchRoom 참가자 추가" - {
        "given: 참가자가 2명인 방이 있을 때" - {
            "when: addParticipant() 를 호출하면" - {
                "then: 3명 미만이므로 isActive=false 를 유지하고 hasCapacity=true 다" {
                    val room = groupMatchRoomRepository.save(GroupMatchRoomFixture.create(quizSetId = 1L))
                    room.addParticipant()
                    room.addParticipant()

                    room.participantCount shouldBe 2
                    room.isActive shouldBe false
                    room.hasCapacity() shouldBe true
                }
            }
        }

        "given: 참가자가 2명인 방이 있을 때" - {
            "when: addParticipant() 로 3번째 참가자를 추가하면" - {
                "then: 3명 이상이므로 isActive=true 로 전환되고 hasCapacity=false 다" {
                    val room = groupMatchRoomRepository.save(GroupMatchRoomFixture.create(quizSetId = 1L))
                    room.addParticipant()
                    room.addParticipant()
                    room.addParticipant()

                    room.participantCount shouldBe 3
                    room.isActive shouldBe true
                    room.hasCapacity() shouldBe false
                }
            }
        }
    }

    "GroupMatchRoomMember" - {
        "given: 멤버1이 방1에 이미 입장해 있을 때" - {
            "when: 멤버1을 방1에 다시 추가하면" - {
                "then: (room_id, member_id) UK 위반으로 예외가 발생한다" {
                    val room = groupMatchRoomRepository.save(GroupMatchRoomFixture.create(quizSetId = 1L))
                    groupMatchRoomMemberRepository.save(GroupMatchRoomMember.of(roomId = room.id, memberId = 1L))

                    shouldThrow<Exception> {
                        groupMatchRoomMemberRepository.saveAndFlush(
                            GroupMatchRoomMember.of(roomId = room.id, memberId = 1L),
                        )
                    }
                }
            }
        }

        "given: quizSetId=1 로 만들어진 방1, 방2 가 있을 때" - {
            "when: 멤버1을 방1과 방2에 각각 추가하면" - {
                "then: room_id 가 달라 UK 에 걸리지 않으므로 둘 다 정상 저장된다" {
                    val room1 = groupMatchRoomRepository.save(GroupMatchRoomFixture.create(quizSetId = 1L))
                    val room2 = groupMatchRoomRepository.save(GroupMatchRoomFixture.create(quizSetId = 1L))

                    groupMatchRoomMemberRepository.save(GroupMatchRoomMember.of(roomId = room1.id, memberId = 1L))
                    val member2 = groupMatchRoomMemberRepository.save(
                        GroupMatchRoomMember.of(roomId = room2.id, memberId = 1L),
                    )

                    member2.id shouldNotBe 0L
                }
            }
        }
    }

    "GroupMatchDecline" - {
        "given: 멤버1이 quizSetId=1 을 이미 거절했을 때" - {
            "when: 동일 조합으로 다시 거절하면" - {
                "then: (quiz_set_id, member_id) UK 위반으로 예외가 발생한다" {
                    groupMatchDeclineRepository.save(GroupMatchDecline.of(quizSetId = 1L, memberId = 1L))

                    shouldThrow<Exception> {
                        groupMatchDeclineRepository.saveAndFlush(
                            GroupMatchDecline.of(quizSetId = 1L, memberId = 1L),
                        )
                    }
                }
            }
        }

        "given: 멤버1이 quizSetId=1 을 거절했을 때" - {
            "when: quizSetId=2 를 거절하면" - {
                "then: quizSetId 가 달라 UK 에 걸리지 않으므로 정상 저장된다" {
                    groupMatchDeclineRepository.save(GroupMatchDecline.of(quizSetId = 1L, memberId = 1L))
                    val decline2 = groupMatchDeclineRepository.save(
                        GroupMatchDecline.of(quizSetId = 2L, memberId = 1L),
                    )

                    decline2.id shouldNotBe 0L
                }
            }
        }
    }
})
