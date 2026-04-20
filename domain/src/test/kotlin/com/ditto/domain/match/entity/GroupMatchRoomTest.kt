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
        "GroupMatchRoom을 생성하고 저장할 수 있다" {
            val room = groupMatchRoomRepository.save(GroupMatchRoomFixture.create(quizSetId = 1L))

            room.id shouldNotBe 0L
            room.quizSetId shouldBe 1L
            room.isActive shouldBe false
            room.participantCount shouldBe 0
        }

        "동일 quizSetId로 방을 여러 개 생성할 수 있다" {
            val room1 = groupMatchRoomRepository.save(GroupMatchRoomFixture.create(quizSetId = 1L))
            val room2 = groupMatchRoomRepository.save(GroupMatchRoomFixture.create(quizSetId = 1L))

            room1.id shouldNotBe room2.id
        }
    }

    "GroupMatchRoom 참가자 추가" - {
        "참가자가 3명 미만이면 isActive가 false다" {
            val room = groupMatchRoomRepository.save(GroupMatchRoomFixture.create(quizSetId = 1L))
            room.addParticipant()
            room.addParticipant()

            room.participantCount shouldBe 2
            room.isActive shouldBe false
            room.hasCapacity() shouldBe true
        }

        "참가자가 3명이 되면 isActive가 true로 전환된다" {
            val room = groupMatchRoomRepository.save(GroupMatchRoomFixture.create(quizSetId = 1L))
            room.addParticipant()
            room.addParticipant()
            room.addParticipant()

            room.participantCount shouldBe 3
            room.isActive shouldBe true
            room.hasCapacity() shouldBe false
        }
    }

    "GroupMatchRoomMember" - {
        "같은 방에 같은 멤버를 중복 추가하면 예외가 발생한다" {
            val room = groupMatchRoomRepository.save(GroupMatchRoomFixture.create(quizSetId = 1L))
            groupMatchRoomMemberRepository.save(GroupMatchRoomMember.of(roomId = room.id, memberId = 1L))

            shouldThrow<Exception> {
                groupMatchRoomMemberRepository.saveAndFlush(
                    GroupMatchRoomMember.of(roomId = room.id, memberId = 1L),
                )
            }
        }

        "한 멤버가 동일 퀴즈셋의 서로 다른 방에 참여할 수 있다" {
            val room1 = groupMatchRoomRepository.save(GroupMatchRoomFixture.create(quizSetId = 1L))
            val room2 = groupMatchRoomRepository.save(GroupMatchRoomFixture.create(quizSetId = 1L))

            groupMatchRoomMemberRepository.save(GroupMatchRoomMember.of(roomId = room1.id, memberId = 1L))
            val member2 = groupMatchRoomMemberRepository.save(
                GroupMatchRoomMember.of(roomId = room2.id, memberId = 1L),
            )

            member2.id shouldNotBe 0L
        }
    }

    "GroupMatchDecline" - {
        "같은 퀴즈셋에 같은 멤버가 중복 거절하면 예외가 발생한다" {
            groupMatchDeclineRepository.save(GroupMatchDecline.of(quizSetId = 1L, memberId = 1L))

            shouldThrow<Exception> {
                groupMatchDeclineRepository.saveAndFlush(
                    GroupMatchDecline.of(quizSetId = 1L, memberId = 1L),
                )
            }
        }

        "같은 멤버가 다른 퀴즈셋을 거절하는 것은 허용된다" {
            groupMatchDeclineRepository.save(GroupMatchDecline.of(quizSetId = 1L, memberId = 1L))
            val decline2 = groupMatchDeclineRepository.save(
                GroupMatchDecline.of(quizSetId = 2L, memberId = 1L),
            )

            decline2.id shouldNotBe 0L
        }
    }
})
