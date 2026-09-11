package com.ditto.domain.match.entity

import com.ditto.domain.match.GroupMatchFixture
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.support.IntegrationTest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import javax.sql.DataSource

class GroupMatchTest(
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "후보 그룹 생성" - {
        "배치가 만든 후보 그룹은 비활성·수락자 0으로 시작한다" {
            val room = groupMatchRepository.save(GroupMatch.candidate(quizSetId = 1L, score = 92.5))

            room.id shouldNotBe 0L
            room.quizSetId shouldBe 1L
            room.score shouldBe 92.5
            room.isActive shouldBe false
            room.participantCount shouldBe 0
        }

        "한 퀴즈셋에 여러 후보 그룹을 만들 수 있다" {
            val room1 = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = 1L))
            val room2 = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = 1L))

            room1.id shouldNotBe room2.id
        }
    }

    "수락 기록" - {
        "최소 인원에 닿기 전에는 성사되지 않는다" {
            val room = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = 1L))

            room.recordAcceptance()
            room.recordAcceptance()

            room.participantCount shouldBe 2
            room.isActive shouldBe false
        }

        "수락자가 3명이 되면 성사된다" {
            val room = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = 1L))

            repeat(3) { room.recordAcceptance() }

            room.participantCount shouldBe 3
            room.isActive shouldBe true
        }
    }

    "초대 응답" - {
        "후보로 배정된 멤버는 대기 상태다" {
            val invitation = GroupMatchMember.candidate(roomId = 1L, memberId = 1L)

            invitation.status shouldBe InvitationStatus.PENDING
            invitation.isPending() shouldBe true
        }

        "수락하면 ACCEPTED, 거절하면 DECLINED 가 된다" {
            val accepted = GroupMatchMember.candidate(roomId = 1L, memberId = 1L).apply { accept() }
            val declined = GroupMatchMember.candidate(roomId = 1L, memberId = 2L).apply { decline() }

            accepted.status shouldBe InvitationStatus.ACCEPTED
            accepted.isPending() shouldBe false
            declined.status shouldBe InvitationStatus.DECLINED
            declined.isPending() shouldBe false
        }
    }

    "GroupMatchMember 유일 제약" - {
        "같은 방에 같은 멤버를 두 번 넣을 수 없다" {
            val room = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = 1L))
            groupMatchMemberRepository.save(GroupMatchMember.candidate(roomId = room.id, memberId = 1L))

            shouldThrow<Exception> {
                groupMatchMemberRepository.saveAndFlush(
                    GroupMatchMember.candidate(roomId = room.id, memberId = 1L),
                )
            }
        }

        "한 멤버가 같은 퀴즈셋의 여러 후보 그룹에 속할 수 있다" {
            val room1 = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = 1L))
            val room2 = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = 1L))

            groupMatchMemberRepository.save(GroupMatchMember.candidate(roomId = room1.id, memberId = 1L))
            val second = groupMatchMemberRepository.save(
                GroupMatchMember.candidate(roomId = room2.id, memberId = 1L),
            )

            second.id shouldNotBe 0L
        }
    }

    "existsByMemberIdAndQuizSetId" - {
        "그 퀴즈셋의 후보 그룹에 속해 있으면 true 다" {
            val room = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = 1L))
            groupMatchMemberRepository.save(GroupMatchMember.candidate(roomId = room.id, memberId = 1L))

            groupMatchMemberRepository.existsByMemberIdAndQuizSetId(memberId = 1L, quizSetId = 1L) shouldBe true
        }

        "다른 퀴즈셋으로 조회하면 false 다" {
            val room = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = 1L))
            groupMatchMemberRepository.save(GroupMatchMember.candidate(roomId = room.id, memberId = 1L))

            groupMatchMemberRepository.existsByMemberIdAndQuizSetId(memberId = 1L, quizSetId = 2L) shouldBe false
        }

        "어느 후보 그룹에도 속하지 않으면 false 다" {
            groupMatchRepository.save(GroupMatchFixture.create(quizSetId = 1L))

            groupMatchMemberRepository.existsByMemberIdAndQuizSetId(memberId = 1L, quizSetId = 1L) shouldBe false
        }
    }
})
