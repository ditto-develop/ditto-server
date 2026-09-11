package com.ditto.api.match

import com.ditto.api.match.service.GroupMatchService
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.match.entity.GroupMatch
import com.ditto.domain.match.entity.GroupMatchMember
import com.ditto.domain.match.entity.InvitationStatus
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import javax.sql.DataSource

class GroupMatchServiceTest(
    private val groupMatchService: GroupMatchService,
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    dataSource: DataSource,
) : IntegrationTest(
    dataSource,
    {

        val quizSetId = 10L

        fun saveCandidateGroup(memberIds: List<Long>, quizSet: Long = quizSetId, score: Double = 80.0): Long {
            val room = groupMatchRepository.save(GroupMatch.candidate(quizSet, score))
            groupMatchMemberRepository.saveAll(memberIds.map { GroupMatchMember.candidate(room.id, it) })
            return room.id
        }

        fun statusOf(roomId: Long, memberId: Long): InvitationStatus =
            groupMatchMemberRepository.findByRoomIdAndMemberId(roomId, memberId)!!.status

        "수락" - {

            "수락하면 내 상태가 ACCEPTED 가 되고 수락자 수가 늘어난다" {
                val roomId = saveCandidateGroup(listOf(1L, 2L, 3L, 4L))

                val result = groupMatchService.acceptGroupMatch(1L, roomId)

                result.acceptedCount shouldBe 1
                result.isFormed shouldBe false
                statusOf(roomId, 1L) shouldBe InvitationStatus.ACCEPTED
            }

            "후보로 배정되지 않은 그룹은 수락할 수 없다" {
                val roomId = saveCandidateGroup(listOf(2L, 3L, 4L))

                val exception = shouldThrow<WarnException> { groupMatchService.acceptGroupMatch(1L, roomId) }

                exception.errorCode shouldBe ErrorCode.FORBIDDEN
            }

            "없는 그룹은 수락할 수 없다" {
                val exception = shouldThrow<WarnException> { groupMatchService.acceptGroupMatch(1L, 999L) }

                exception.errorCode shouldBe ErrorCode.NOT_FOUND
            }

            "이미 수락한 초대는 다시 수락할 수 없다" {
                val roomId = saveCandidateGroup(listOf(1L, 2L, 3L))
                groupMatchService.acceptGroupMatch(1L, roomId)

                val exception = shouldThrow<WarnException> { groupMatchService.acceptGroupMatch(1L, roomId) }

                exception.errorCode shouldBe ErrorCode.ALREADY_JOINED_GROUP
            }

            "이미 거절한 초대는 수락할 수 없다" {
                val roomId = saveCandidateGroup(listOf(1L, 2L, 3L))
                groupMatchService.declineGroupMatch(1L, roomId)

                val exception = shouldThrow<WarnException> { groupMatchService.acceptGroupMatch(1L, roomId) }

                exception.errorCode shouldBe ErrorCode.ALREADY_DECLINED_GROUP
            }
        }

        "성사" - {

            "수락자가 최소 인원에 닿기 전에는 채팅방이 열리지 않는다" {
                val roomId = saveCandidateGroup(listOf(1L, 2L, 3L, 4L))

                groupMatchService.acceptGroupMatch(1L, roomId)
                groupMatchService.acceptGroupMatch(2L, roomId)

                groupMatchRepository.findById(roomId).get().isActive shouldBe false
                chatRoomRepository.findBySourceTypeAndSourceId(ChatRoomType.GROUP, roomId) shouldBe null
            }

            "수락자가 최소 인원에 닿으면 성사되고 채팅방이 열린다" {
                val roomId = saveCandidateGroup(listOf(1L, 2L, 3L, 4L))

                groupMatchService.acceptGroupMatch(1L, roomId)
                groupMatchService.acceptGroupMatch(2L, roomId)
                val result = groupMatchService.acceptGroupMatch(3L, roomId)

                result.isFormed shouldBe true
                result.acceptedCount shouldBe 3
                groupMatchRepository.findById(roomId).get().isActive shouldBe true
                chatRoomRepository.findBySourceTypeAndSourceId(ChatRoomType.GROUP, roomId)
                    ?.sourceId shouldBe roomId
            }

            "성사 뒤에 수락한 사람도 채팅방에 들어간다" {
                // 정원(4)이 성사 최소 인원(3)보다 커서 4번째 수락이 정상 경로다.
                val roomId = saveCandidateGroup(listOf(1L, 2L, 3L, 4L))
                groupMatchService.acceptGroupMatch(1L, roomId)
                groupMatchService.acceptGroupMatch(2L, roomId)
                groupMatchService.acceptGroupMatch(3L, roomId)

                val result = groupMatchService.acceptGroupMatch(4L, roomId)

                result.acceptedCount shouldBe 4
                result.isFormed shouldBe true
                val chatRoom = chatRoomRepository.findBySourceTypeAndSourceId(ChatRoomType.GROUP, roomId)!!
                chatRoomMemberRepository.findByRoomIdIn(listOf(chatRoom.id))
                    .map { it.memberId } shouldContainExactlyInAnyOrder listOf(1L, 2L, 3L, 4L)
            }

            "채팅방에는 수락한 사람만 들어간다" {
                val roomId = saveCandidateGroup(listOf(1L, 2L, 3L, 4L))
                groupMatchService.declineGroupMatch(4L, roomId)

                groupMatchService.acceptGroupMatch(1L, roomId)
                groupMatchService.acceptGroupMatch(2L, roomId)
                groupMatchService.acceptGroupMatch(3L, roomId)

                val chatRoom = chatRoomRepository.findBySourceTypeAndSourceId(ChatRoomType.GROUP, roomId)!!
                chatRoomMemberRepository.findByRoomIdIn(listOf(chatRoom.id))
                    .map { it.memberId } shouldContainExactlyInAnyOrder listOf(1L, 2L, 3L)
            }
        }

        "자동 거절" - {

            "한 그룹을 수락하면 같은 퀴즈셋의 남은 초대는 자동 거절된다" {
                val acceptedRoomId = saveCandidateGroup(listOf(1L, 2L, 3L))
                val otherRoomId = saveCandidateGroup(listOf(1L, 4L, 5L))

                groupMatchService.acceptGroupMatch(1L, acceptedRoomId)

                statusOf(otherRoomId, 1L) shouldBe InvitationStatus.DECLINED
                // 그 그룹의 다른 구성원은 그대로 대기한다 — 성사 가능성만 낮아진다
                statusOf(otherRoomId, 4L) shouldBe InvitationStatus.PENDING
            }

            "다른 퀴즈셋의 초대는 건드리지 않는다" {
                val acceptedRoomId = saveCandidateGroup(listOf(1L, 2L, 3L))
                val otherQuizSetRoomId = saveCandidateGroup(listOf(1L, 4L, 5L), quizSet = 99L)

                groupMatchService.acceptGroupMatch(1L, acceptedRoomId)

                statusOf(otherQuizSetRoomId, 1L) shouldBe InvitationStatus.PENDING
            }
        }

        "거절" - {

            "거절하면 내 상태가 DECLINED 가 된다" {
                val roomId = saveCandidateGroup(listOf(1L, 2L, 3L))

                groupMatchService.declineGroupMatch(1L, roomId)

                statusOf(roomId, 1L) shouldBe InvitationStatus.DECLINED
                groupMatchRepository.findById(roomId).get().acceptedCount shouldBe 0
            }

            "이미 응답한 초대는 거절할 수 없다" {
                val roomId = saveCandidateGroup(listOf(1L, 2L, 3L))
                groupMatchService.declineGroupMatch(1L, roomId)

                val exception = shouldThrow<WarnException> { groupMatchService.declineGroupMatch(1L, roomId) }

                exception.errorCode shouldBe ErrorCode.ALREADY_DECLINED_GROUP
            }
        }
    },
)
