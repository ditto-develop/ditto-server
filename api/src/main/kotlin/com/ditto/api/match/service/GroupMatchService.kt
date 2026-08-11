package com.ditto.api.match.service

import com.ditto.api.chat.service.ChatService
import com.ditto.api.match.dto.GroupMatchDeclineRequest
import com.ditto.api.match.dto.GroupMatchJoinRequest
import com.ditto.api.match.dto.GroupMatchJoinResponse
import com.ditto.api.notification.message.NotificationMessages
import com.ditto.api.notification.service.NotificationAppender
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.match.entity.GroupMatch
import com.ditto.domain.match.entity.GroupMatchDecline
import com.ditto.domain.match.entity.GroupMatchMember
import com.ditto.domain.match.repository.GroupMatchDeclineRepository
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class GroupMatchService(
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    private val groupMatchDeclineRepository: GroupMatchDeclineRepository,
    private val chatService: ChatService,
    private val notificationAppender: NotificationAppender,
) {

    /** 그룹 매칭 참여 */
    @Transactional
    fun joinGroupMatch(memberId: Long, request: GroupMatchJoinRequest): GroupMatchJoinResponse {
        val quizSetId = request.quizSetId

        if (groupMatchDeclineRepository.existsByQuizSetIdAndMemberId(quizSetId, memberId)) {
            throw WarnException(ErrorCode.ALREADY_DECLINED_GROUP)
        }

        if (groupMatchMemberRepository.existsByMemberIdAndQuizSetId(memberId, quizSetId)) {
            throw WarnException(ErrorCode.ALREADY_JOINED_GROUP)
        }

        val room = findOrCreateRoom(quizSetId)
        room.addParticipant()
        groupMatchMemberRepository.save(GroupMatchMember.of(room.id, memberId))

        // 방이 막 활성화(참가자 임계값 도달)됐다면 참가자 전원의 채팅방을 생성한다.
        // findOrCreateRoom 은 비활성 방만 반환하므로, isActive == true 는 이번 참여로 활성화됐음을 뜻한다.
        if (room.isActive) {
            val memberIds = groupMatchMemberRepository.findByRoomId(room.id).map { it.memberId }
            val chatRoomId = chatService.createGroupRoom(room.id, memberIds)
            // 그룹이 구성됐다는 사실을 아는 곳이 여기뿐이다. 적재는 자기 트랜잭션에서 즉시 커밋되므로,
            // 그 뒤 커밋 시점 flush 가 실패해 이 트랜잭션이 롤백되면 그룹도 채팅방도 없이 알림만 남는다
            // (`createGroupRoom` 은 REQUIRED 라 같은 트랜잭션이다).
            // 통지 하나가 유실되는 쪽보다 낫다고 보고 감수한다.
            notificationAppender.appendAll(
                memberIds = memberIds,
                content = NotificationMessages.groupFormed(memberIds.size),
                targetId = chatRoomId,
            )
        }

        return GroupMatchJoinResponse.from(room)
    }

    /** 그룹 매칭 거절 */
    @Transactional
    fun declineGroupMatch(memberId: Long, request: GroupMatchDeclineRequest) {
        val quizSetId = request.quizSetId

        if (groupMatchDeclineRepository.existsByQuizSetIdAndMemberId(quizSetId, memberId)) {
            throw WarnException(ErrorCode.ALREADY_DECLINED_GROUP)
        }

        groupMatchDeclineRepository.save(GroupMatchDecline.of(quizSetId, memberId))
    }

    /** 참여 가능한 방이 있으면 반환, 없으면 새 방 생성 */
    private fun findOrCreateRoom(quizSetId: Long): GroupMatch {
        val existingRoom = groupMatchRepository
            .findFirstByQuizSetIdAndIsActiveFalseOrderByCreatedAtAsc(quizSetId)

        return existingRoom ?: groupMatchRepository.save(GroupMatch.create(quizSetId))
    }
}
