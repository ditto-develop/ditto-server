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
     * **잠금을 방 → 내 초대 순서로 건다**(ADR 0011 규칙 3).
     *
     * 방 잠금은 같은 그룹에 대한 동시 수락을 직렬화한다. 없으면 둘 다 낡은 수락자 수를 보고 임계값에
     * 닿았다고 판단해 채팅방을 만들려다 `chat_room` 유일키 충돌로 한쪽이 통째로 롤백된다.
     * 내 초대 잠금은 한 회원이 **서로 다른 두 그룹**을 동시에 수락하는 경쟁을 막는다 — 방만 잠그면
     * 각자 다른 행을 잠그므로 둘 다 통과해 "한 주에 채팅방 하나"가 깨진다.
     *
     * 두 잠금 모두 이 트랜잭션에서 해당 엔티티의 첫 접근이다(규칙 5).
     */
    @Transactional
    fun acceptGroupMatch(memberId: Long, groupMatchId: Long): GroupMatchAcceptResponse {
        val room = groupMatchRepository.findWithLockById(groupMatchId)
            ?: throw WarnException(ErrorCode.NOT_FOUND)
        val myInvitations = groupMatchMemberRepository.findWithLockByMemberId(memberId)

        val invitation = pendingInvitationIn(myInvitations, groupMatchId)
        invitation.accept()
        val justFormed = room.recordAcceptance()
        declineOtherInvitations(myInvitations, room.quizSetId, acceptedGroupMatchId = groupMatchId)

        // 정원이 최소 인원보다 커서 성사 뒤에도 수락이 들어온다. 그때는 방을 새로 여는 게 아니라
        // 이미 열린 방에 이 사람만 붙여야 한다 — createGroupRoom 은 방이 있으면 곧바로 돌아간다.
        when {
            justFormed -> openGroupChatAndNotify(groupMatchId)
            room.isActive -> joinFormedChatAndNotify(groupMatchId, memberId, room.acceptedCount)
        }
        return GroupMatchAcceptResponse.from(room)
    }

    /** 그룹 초대를 거절한다. 내 초대 한 행만 바꾸고 수락자 수도 건드리지 않으므로 잠그지 않는다. */
    @Transactional
    fun declineGroupMatch(memberId: Long, groupMatchId: Long) {
        val invitation = groupMatchMemberRepository.findByRoomIdAndMemberId(groupMatchId, memberId)
            ?: throw WarnException(ErrorCode.FORBIDDEN)

        requirePending(invitation).decline()
    }

    /**
     * 한 주에 열리는 채팅방은 하나뿐이라, 한 그룹을 수락하면 같은 퀴즈셋의 남은 초대는 자동 거절한다.
     * 거절당한 그룹의 다른 구성원에게는 알리지 않는다 — 그들에게는 성사 가능성이 낮아졌을 뿐이다.
     */
    private fun declineOtherInvitations(
        myInvitations: List<GroupMatchMember>,
        quizSetId: Long,
        acceptedGroupMatchId: Long,
    ) {
        val otherPending = myInvitations.filter { it.roomId != acceptedGroupMatchId && it.isPending() }
        if (otherPending.isEmpty()) return

        // 잠긴 초대는 회원 전체(여러 주)라 이번 퀴즈셋 것만 골라낸다. quizSetId 는 불변이라 잠그지 않고 읽는다.
        val sameQuizSetRoomIds = groupMatchRepository.findAllById(otherPending.map { it.roomId })
            .filter { it.quizSetId == quizSetId }
            .map { it.id }
            .toSet()

        otherPending.filter { it.roomId in sameQuizSetRoomIds }.forEach { it.decline() }
    }

    private fun pendingInvitationIn(myInvitations: List<GroupMatchMember>, groupMatchId: Long): GroupMatchMember {
        val invitation = myInvitations.firstOrNull { it.roomId == groupMatchId }
            ?: throw WarnException(ErrorCode.FORBIDDEN)

        return requirePending(invitation)
    }

    private fun requirePending(invitation: GroupMatchMember): GroupMatchMember =
        when (invitation.status) {
            InvitationStatus.PENDING -> invitation
            InvitationStatus.ACCEPTED -> throw WarnException(ErrorCode.ALREADY_JOINED_GROUP)
            InvitationStatus.DECLINED -> throw WarnException(ErrorCode.ALREADY_DECLINED_GROUP)
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
