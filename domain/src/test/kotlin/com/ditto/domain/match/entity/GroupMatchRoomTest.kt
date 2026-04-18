package com.ditto.domain.match.entity

import com.ditto.domain.match.GroupMatchRoomFixture
import com.ditto.domain.match.repository.GroupMatchRoomRepository
import com.ditto.domain.support.IntegrationTest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import javax.sql.DataSource

class GroupMatchRoomTest(
    private val groupMatchRoomRepository: GroupMatchRoomRepository,
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

        "같은 quizSetId로 중복 저장하면 예외가 발생한다" {
            groupMatchRoomRepository.save(GroupMatchRoomFixture.create(quizSetId = 1L))

            shouldThrow<Exception> {
                groupMatchRoomRepository.saveAndFlush(GroupMatchRoomFixture.create(quizSetId = 1L))
            }
        }
    }

    "GroupMatchRoom 참가자 추가" - {
        "참가자가 3명 미만이면 isActive가 false다" {
            val room = groupMatchRoomRepository.save(GroupMatchRoomFixture.create(quizSetId = 1L))
            room.addParticipant()
            room.addParticipant()

            room.participantCount shouldBe 2
            room.isActive shouldBe false
        }

        "참가자가 3명 이상이 되면 isActive가 true로 전환된다" {
            val room = groupMatchRoomRepository.save(GroupMatchRoomFixture.create(quizSetId = 1L))
            room.addParticipant()
            room.addParticipant()
            room.addParticipant()

            room.participantCount shouldBe 3
            room.isActive shouldBe true
        }
    }
})
