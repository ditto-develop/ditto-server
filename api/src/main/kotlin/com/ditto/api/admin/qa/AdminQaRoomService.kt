package com.ditto.api.admin.qa

import com.ditto.api.admin.qa.dto.QaMember
import com.ditto.api.admin.qa.dto.QaRoomMember
import com.ditto.api.admin.qa.dto.QaRoomMessage
import com.ditto.api.admin.qa.dto.QaRoomSummary
import com.ditto.api.admin.qa.dto.QaRoomView
import com.ditto.api.admin.qa.dto.QaVote
import com.ditto.api.admin.qa.dto.QaVoteOption
import com.ditto.api.chat.dto.ChatVoteDetailResponse
import com.ditto.api.chat.service.ChatVoteService
import com.ditto.api.system.ServerTimeProvider
import com.ditto.domain.chat.entity.ChatMessage
import com.ditto.domain.chat.entity.ChatMessageType
import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.entity.ChatRoomMember
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.entity.ChatVoteStatus
import com.ditto.domain.chat.repository.ChatMessageRepository
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * QA 콘솔의 채팅방 조회. 어드민은 방 멤버가 아니라서 앱의 조회 API를 쓰지 않고 저장소를 직접 읽는다.
 * 나간 사람의 커서와 이탈 표시까지 한 화면에 보여야 하기도 하다.
 */
@Service
@Transactional(readOnly = true)
class AdminQaRoomService(
    private val qaDummies: QaDummies,
    private val qaMemberLabels: QaMemberLabels,
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatVoteService: ChatVoteService,
    private val serverTimeProvider: ServerTimeProvider,
    private val qaRoomSourceLabels: QaRoomSourceLabels,
) {
    /** 더미가 들어 있던 방. 진행 중인 방을 먼저, 그 안에서는 최근 방을 먼저 둔다. */
    fun getRoomSummaries(): List<QaRoomSummary> {
        val dummyIds = qaDummies.findIds()
        if (dummyIds.isEmpty()) return emptyList()

        val roomIds = chatRoomMemberRepository.findByMemberIdIn(dummyIds).map { it.roomId }.toSet()
        if (roomIds.isEmpty()) return emptyList()

        val rooms = chatRoomRepository.findAllById(roomIds)
            .sortedWith(compareBy<ChatRoom> { it.isEnded }.thenByDescending { it.id })
        val activeMembersByRoomId = chatRoomMemberRepository.findByRoomIdIn(roomIds)
            .filterNot { it.hasLeft }
            .groupBy { it.roomId }
        val everyActiveMemberId = activeMembersByRoomId.values.flatten().map { it.memberId }
        val members = qaMemberLabels.load(everyActiveMemberId)
        val sourceLabels = qaRoomSourceLabels.of(rooms)
        val lastMessageAtByRoomId = chatMessageRepository.findLastMessageTimes(roomIds)
            .associate { it.roomId to it.lastMessageAt }

        return rooms.map { room ->
            val activeMemberIds = activeMembersByRoomId[room.id].orEmpty().map { it.memberId }
            QaRoomSummary(
                roomId = room.id,
                sourceType = room.sourceType,
                sourceLabel = sourceLabels[room.id],
                status = room.status,
                opensAt = room.opensAt,
                expiresAt = room.expiresAt,
                realMembers = activeMemberIds.filterNot { it in dummyIds }.map(members::of),
                dummyCount = activeMemberIds.count { it in dummyIds },
                lastMessageAt = lastMessageAtByRoomId[room.id],
            )
        }
    }

    /** 최근 메시지 [TIMELINE_SIZE]개를 오래된 순서로 준다. 방이 없으면 null. */
    fun findRoom(roomId: Long): QaRoomView? {
        val room = chatRoomRepository.findByIdOrNull(roomId) ?: return null
        val dummyIds = qaDummies.findIds()
        val roomMembers = chatRoomMemberRepository.findByRoomId(roomId)
        val messages = chatMessageRepository.findByRoomIdWithCursor(roomId, cursor = null, size = TIMELINE_SIZE)
            .reversed()
        val memberIds = roomMembers.map { it.memberId } + messages.map { it.senderId }
        val members = qaMemberLabels.load(memberIds)
        val rows = RoomRows(members, dummyIds)
        val votes = findVotes(room, roomMembers)

        return QaRoomView(
            now = serverTimeProvider.now(),
            roomId = room.id,
            sourceType = room.sourceType,
            status = room.status,
            opensAt = room.opensAt,
            expiresAt = room.expiresAt,
            endReason = room.endReason,
            members = roomMembers.sortedWith(compareBy({ it.memberId in dummyIds }, { it.memberId })).map(rows::member),
            messages = messages.map { rows.message(it, roomMembers) },
            votes = votes.map { it.toQaVote(members) },
            canEndByUser = room.canEndByUser(),
            leaveDissolvesRoom = room.dissolvesWhenOneLeaves(activeMemberCount = roomMembers.count { !it.hasLeft }),
        )
    }

    fun findVote(roomId: Long, voteId: Long): QaVote? {
        val room = chatRoomRepository.findByIdOrNull(roomId) ?: return null
        val roomMembers = chatRoomMemberRepository.findByRoomId(roomId)
        val vote = findVotes(room, roomMembers).firstOrNull { it.voteId == voteId } ?: return null
        return vote.toQaVote(qaMemberLabels.load(roomMembers.map { it.memberId }))
    }

    fun findLatestMessageId(roomId: Long): Long? = chatMessageRepository.findFirstByRoomIdOrderByIdDesc(roomId)?.id

    fun findActiveDummiesIn(roomId: Long): List<QaMember> {
        val dummyIds = qaDummies.findIds()
        val activeDummyIds = chatRoomMemberRepository.findByRoomId(roomId)
            .filter { !it.hasLeft && it.memberId in dummyIds }
            .map { it.memberId }
        val members = qaMemberLabels.load(activeDummyIds)
        return activeDummyIds.map(members::of)
    }

    /**
     * 앱 조회는 나가지 않은 방 멤버만 되므로 그중 한 명의 눈으로 읽는다. 집계(voterIds)는 보는 사람과 무관하다.
     * 모두 나간 방은 앱에서도 투표를 볼 사람이 없어 비워 둔다.
     */
    private fun findVotes(room: ChatRoom, roomMembers: List<ChatRoomMember>): List<ChatVoteDetailResponse> {
        if (room.sourceType != ChatRoomType.GROUP) return emptyList()
        val viewerId = roomMembers.firstOrNull { !it.hasLeft }?.memberId ?: return emptyList()
        return chatVoteService.getVotes(room.id, viewerId)
    }

    private fun ChatVoteDetailResponse.toQaVote(members: QaMembers): QaVote =
        QaVote(
            voteId = voteId,
            isOpen = status == ChatVoteStatus.OPEN,
            allowMultiple = allowMultiple,
            votedCount = votedCount,
            totalMembers = totalMembers,
            closedReason = closedReason,
            placeOptions = placeOptions.map { QaVoteOption(it.optionId, it.label, it.voterIds.map(members::of)) },
            timeOptions = timeOptions.map {
                QaVoteOption(it.optionId, QaTimeFormat.format(it.meetAt), it.voterIds.map(members::of))
            },
        )

    /** 방 화면의 참여자·메시지 한 줄씩. 안읽음 수는 나가지 않은 멤버의 커서로 센다(앱 메시지 응답과 같은 규칙). */
    private class RoomRows(
        private val members: QaMembers,
        private val dummyIds: Set<Long>,
    ) {
        fun member(roomMember: ChatRoomMember) = QaRoomMember(
            member = members.of(roomMember.memberId),
            isDummy = roomMember.memberId in dummyIds,
            hasLeft = roomMember.hasLeft,
            lastReadMessageId = roomMember.lastReadMessageId,
        )

        fun message(message: ChatMessage, roomMembers: List<ChatRoomMember>) = QaRoomMessage(
            messageId = message.id,
            sender = members.of(message.senderId),
            isFromDummy = message.senderId in dummyIds,
            messageType = message.messageType,
            content = message.content,
            systemMeaning = message.content.takeIf { message.messageType == ChatMessageType.SYSTEM }
                ?.let(QaSystemMessageMeaning::of),
            sentAt = message.createdAt,
            unreadCount = message.unreadCountAmong(roomMembers),
        )
    }

    companion object {
        const val TIMELINE_SIZE = 50
    }
}
