package com.ditto.api.match.service

import com.ditto.api.chat.service.ChatService
import com.ditto.api.match.dto.GroupMatchAcceptResponse
import com.ditto.api.notification.message.NotificationMessages
import com.ditto.api.notification.service.NotificationAppender
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.match.entity.GroupMatchMember
import com.ditto.domain.match.entity.InvitationStatus
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 후보 그룹 초대에 대한 회원의 응답(수락·거절)을 처리한다.
 *
 * 그룹은 배치가 미리 짜두고([GroupCandidateWriter]) 구성원이 각자 응답한다. 수락 인원이 최소 인원에
 * 닿는 순간 성사되고 채팅방이 열린다. 수락·거절 모두 되돌릴 수 없다.
 */
@Service
@Transactional(readOnly = true)
class GroupMatchService(
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    private val chatService: ChatService,
    private val notificationAppender: NotificationAppender,
) {

    /**
     * 그룹 초대를 수락한다.
     *
     * **방 행을 잠그고 시작한다**(ADR 0011). 동시 수락이 각자 낡은 수락자 수를 보면 성사 판정이 어긋나고,
     * 둘 다 임계값에 닿았다고 판단해 채팅방을 만들면 `chat_room` 유일키 충돌로 한쪽 트랜잭션이 통째로
     * 롤백돼 그 사람의 수락이 사라진다. 잠금 조회는 이 트랜잭션의 첫 접근이어야 하므로 맨 앞에 둔다(규칙 5).
     */
    @Transactional
    fun acceptGroupMatch(memberId: Long, groupMatchId: Long): GroupMatchAcceptResponse {
        val room = groupMatchRepository.findWithLockById(groupMatchId)
            ?: throw WarnException(ErrorCode.NOT_FOUND)

        val invitation = findPendingInvitation(groupMatchId, memberId)
        invitation.accept()
        val justFormed = room.recordAcceptance()
        declineOtherInvitations(memberId, room.quizSetId, acceptedGroupMatchId = groupMatchId)

        // 정원이 최소 인원보다 커서 성사 뒤에도 수락이 들어온다. 그때는 방을 새로 여는 게 아니라
        // 이미 열린 방에 이 사람만 붙여야 한다 — createGroupRoom 은 방이 있으면 곧바로 돌아간다.
        when {
            justFormed -> openGroupChatAndNotify(groupMatchId)
            room.isActive -> joinFormedChatAndNotify(groupMatchId, memberId, room.participantCount)
        }
        return GroupMatchAcceptResponse.from(room)
    }

    /** 그룹 초대를 거절한다. 수락자 수를 건드리지 않으므로 방 행을 잠글 필요가 없다. */
    @Transactional
    fun declineGroupMatch(memberId: Long, groupMatchId: Long) {
        findPendingInvitation(groupMatchId, memberId).decline()
    }

    /**
     * 한 주에 열리는 채팅방은 하나뿐이라, 한 그룹을 수락하면 같은 퀴즈셋의 남은 초대는 자동 거절한다.
     * 거절당한 그룹의 다른 구성원에게는 알리지 않는다 — 그들에게는 성사 가능성이 낮아졌을 뿐이다.
     */
    private fun declineOtherInvitations(memberId: Long, quizSetId: Long, acceptedGroupMatchId: Long) {
        groupMatchMemberRepository
            .findByMemberIdAndQuizSetId(memberId, quizSetId)
            .filter { it.roomId != acceptedGroupMatchId && it.isPending() }
            .forEach { it.decline() }
    }

    private fun findPendingInvitation(groupMatchId: Long, memberId: Long): GroupMatchMember {
        val invitation = groupMatchMemberRepository.findByRoomIdAndMemberId(groupMatchId, memberId)
            ?: throw WarnException(ErrorCode.FORBIDDEN)

        return when (invitation.status) {
            InvitationStatus.PENDING -> invitation
            InvitationStatus.ACCEPTED -> throw WarnException(ErrorCode.ALREADY_JOINED_GROUP)
            InvitationStatus.DECLINED -> throw WarnException(ErrorCode.ALREADY_DECLINED_GROUP)
        }
    }

    /** 성사 뒤에 수락한 사람을 이미 열린 방에 넣고 본인에게만 알린다. 기존 구성원에게는 다시 알리지 않는다. */
    private fun joinFormedChatAndNotify(groupMatchId: Long, memberId: Long, memberCount: Int) {
        val chatRoomId = chatService.addGroupRoomMember(groupMatchId, memberId) ?: return

        notificationAppender.appendAll(
            memberIds = listOf(memberId),
            content = NotificationMessages.groupFormed(memberCount),
            targetId = chatRoomId,
        )
    }

    /**
     * 성사된 그룹의 채팅방을 열고 참가자 전원에게 알린다. **수락한 사람만** 방에 넣는다.
     *
     * 그룹이 성사됐다는 사실을 아는 곳이 여기뿐이라 알림도 이 트랜잭션 안에서 남긴다. 적재는 자기
     * 트랜잭션에서 즉시 커밋되므로, 그 뒤 커밋 시점 flush 가 실패해 이 트랜잭션이 롤백되면 그룹도
     * 채팅방도 없이 알림만 남는다. 통지 하나가 유실되는 쪽보다 낫다고 보고 감수한다.
     */
    private fun openGroupChatAndNotify(groupMatchId: Long) {
        val memberIds = groupMatchMemberRepository.findByRoomId(groupMatchId)
            .filter { it.status == InvitationStatus.ACCEPTED }
            .map { it.memberId }
        val chatRoomId = chatService.createGroupRoom(groupMatchId, memberIds)

        notificationAppender.appendAll(
            memberIds = memberIds,
            content = NotificationMessages.groupFormed(memberIds.size),
            targetId = chatRoomId,
        )
    }
}
