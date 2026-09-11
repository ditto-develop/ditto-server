package com.ditto.domain.match.repository

import com.ditto.domain.match.GroupMatchFixture
import com.ditto.domain.match.entity.GroupMatchMember
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.shouldBe
import javax.sql.DataSource

/**
 * 같은 그룹 채팅방 참여 여부 판정. 이 결과가 타인 프로필·소개노트 열람 권한으로 이어지므로
 * (`MatchAccessChecker`), 후보로 배정만 된 사이가 참으로 새지 않는지 확인한다.
 */
class GroupMatchSharedRoomQueryTest(
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun saveGroup(acceptedCount: Int = 0): Long =
        groupMatchRepository.save(GroupMatchFixture.create(quizSetId = 1L, acceptedCount = acceptedCount)).id

    fun saveMember(roomId: Long, memberId: Long, accepted: Boolean) {
        val invitation = GroupMatchMember.candidate(roomId, memberId)
        if (accepted) invitation.accept()
        groupMatchMemberRepository.save(invitation)
    }

    "existsSharedRoom" - {

        "둘 다 수락했고 그룹이 성사됐으면 true 다" {
            val roomId = saveGroup(acceptedCount = 3)
            saveMember(roomId, 1L, accepted = true)
            saveMember(roomId, 2L, accepted = true)

            groupMatchMemberRepository.existsSharedRoom(1L, 2L) shouldBe true
        }

        "같은 후보 그룹이어도 아무도 수락하지 않았으면 false 다" {
            // 배치가 후보로 묶기만 한 사이 — 프로필이 열리면 안 된다
            val roomId = saveGroup()
            saveMember(roomId, 1L, accepted = false)
            saveMember(roomId, 2L, accepted = false)

            groupMatchMemberRepository.existsSharedRoom(1L, 2L) shouldBe false
        }

        "한쪽만 수락했으면 false 다" {
            val roomId = saveGroup(acceptedCount = 3)
            saveMember(roomId, 1L, accepted = true)
            saveMember(roomId, 2L, accepted = false)

            groupMatchMemberRepository.existsSharedRoom(1L, 2L) shouldBe false
        }

        "둘 다 수락했어도 그룹이 성사되지 않았으면 false 다" {
            val roomId = saveGroup(acceptedCount = 2)
            saveMember(roomId, 1L, accepted = true)
            saveMember(roomId, 2L, accepted = true)

            groupMatchMemberRepository.existsSharedRoom(1L, 2L) shouldBe false
        }

        "서로 다른 그룹이면 false 다" {
            val roomId1 = saveGroup(acceptedCount = 3)
            val roomId2 = saveGroup(acceptedCount = 3)
            saveMember(roomId1, 1L, accepted = true)
            saveMember(roomId2, 2L, accepted = true)

            groupMatchMemberRepository.existsSharedRoom(1L, 2L) shouldBe false
        }
    }

    "existsSharedCandidateGroup" - {

        "같은 후보 그룹에 남아 있으면 응답 전이어도 true 다" {
            // 성사 전 소개노트 미리보기의 근거 — 참여 여부를 정하려면 상대를 볼 수 있어야 한다.
            val roomId = saveGroup()
            saveMember(roomId, 1L, accepted = false)
            saveMember(roomId, 2L, accepted = false)

            groupMatchMemberRepository.existsSharedCandidateGroup(1L, 2L, quizSetId = 1L) shouldBe true
        }

        "조회자가 그 그룹을 거절했으면 false 다" {
            val roomId = saveGroup()
            val mine = GroupMatchMember.candidate(roomId, 1L).apply { decline() }
            groupMatchMemberRepository.save(mine)
            saveMember(roomId, 2L, accepted = false)

            groupMatchMemberRepository.existsSharedCandidateGroup(1L, 2L, quizSetId = 1L) shouldBe false
        }

        "상대가 그 그룹을 거절했으면 false 다" {
            val roomId = saveGroup()
            saveMember(roomId, 1L, accepted = false)
            groupMatchMemberRepository.save(GroupMatchMember.candidate(roomId, 2L).apply { decline() })

            groupMatchMemberRepository.existsSharedCandidateGroup(1L, 2L, quizSetId = 1L) shouldBe false
        }

        "다른 퀴즈셋의 그룹이면 false 다" {
            val roomId = groupMatchRepository.save(
                GroupMatchFixture.create(quizSetId = 2L),
            ).id
            saveMember(roomId, 1L, accepted = false)
            saveMember(roomId, 2L, accepted = false)

            groupMatchMemberRepository.existsSharedCandidateGroup(1L, 2L, quizSetId = 1L) shouldBe false
        }

        "서로 다른 후보 그룹이면 false 다" {
            saveMember(saveGroup(), 1L, accepted = false)
            saveMember(saveGroup(), 2L, accepted = false)

            groupMatchMemberRepository.existsSharedCandidateGroup(1L, 2L, quizSetId = 1L) shouldBe false
        }
    }
})
