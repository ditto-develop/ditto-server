package com.ditto.domain.match.entity

import com.ditto.domain.match.GroupMatchRoomFixture
import com.ditto.domain.match.repository.GroupMatchParticipantRepository
import com.ditto.domain.match.repository.GroupMatchRoomRepository
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import javax.sql.DataSource

class GroupMatchRoomTest(
    private val groupMatchRoomRepository: GroupMatchRoomRepository,
    private val groupMatchParticipantRepository: GroupMatchParticipantRepository,
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

        "동일 quizSetId로 방을 여러 개 생성할 수 있다 (퀴즈셋당 방 1개 제약 없음)" {
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

    "GroupMatchParticipant 유니크 제약" - {
        "동일 quizSetId + memberId 조합은 중복 저장할 수 없다" {
            val room = groupMatchRoomRepository.save(GroupMatchRoomFixture.create(quizSetId = 1L))
            groupMatchParticipantRepository.save(
                GroupMatchParticipant.join(quizSetId = 1L, memberId = 1L, roomId = room.id),
            )

            io.kotest.assertions.throwables.shouldThrow<Exception> {
                groupMatchParticipantRepository.saveAndFlush(
                    GroupMatchParticipant.join(quizSetId = 1L, memberId = 1L, roomId = room.id),
                )
            }
        }

        "같은 멤버도 다른 quizSetId면 각각 참여할 수 있다" {
            val room1 = groupMatchRoomRepository.save(GroupMatchRoomFixture.create(quizSetId = 1L))
            val room2 = groupMatchRoomRepository.save(GroupMatchRoomFixture.create(quizSetId = 2L))

            groupMatchParticipantRepository.save(
                GroupMatchParticipant.join(quizSetId = 1L, memberId = 1L, roomId = room1.id),
            )
            val p2 = groupMatchParticipantRepository.save(
                GroupMatchParticipant.join(quizSetId = 2L, memberId = 1L, roomId = room2.id),
            )

            p2.id shouldNotBe 0L
        }
    }
})
