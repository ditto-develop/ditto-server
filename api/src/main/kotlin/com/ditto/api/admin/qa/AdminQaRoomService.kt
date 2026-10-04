package com.ditto.api.admin.qa

import com.ditto.api.admin.qa.dto.QaRoomMember
import com.ditto.api.admin.qa.dto.QaRoomMessage
import com.ditto.api.admin.qa.dto.QaRoomSummary
import com.ditto.api.admin.qa.dto.QaRoomView
import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.repository.ChatMessageRepository
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.member.repository.MemberRepository
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
    private val memberRepository: MemberRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val chatMessageRepository: ChatMessageRepository,
) {
    /** 더미가 들어 있던 방. 진행 중인 방을 먼저, 그 안에서는 최근 방을 먼저 둔다. */
    fun getRoomSummaries(): List<QaRoomSummary> {
        val dummyIds = qaDummies.findIds()
        if (dummyIds.isEmpty()) return emptyList()

        val roomIds = chatRoomMemberRepository.findByMemberIdIn(dummyIds).map { it.roomId }.toSet()
        if (roomIds.isEmpty()) return emptyList()

        val membersByRoomId = chatRoomMemberRepository.findByRoomIdIn(roomIds).groupBy { it.roomId }
        return chatRoomRepository.findAllById(roomIds)
            .sortedWith(compareBy<ChatRoom> { it.isEnded }.thenByDescending { it.id })
            .map { room ->
                val activeMembers = membersByRoomId[room.id].orEmpty().filterNot { it.hasLeft }
                QaRoomSummary(
                    roomId = room.id,
                    sourceType = room.sourceType,
                    status = room.status,
                    opensAt = room.opensAt,
                    expiresAt = room.expiresAt,
                    memberCount = activeMembers.size,
                    dummyCount = activeMembers.count { it.memberId in dummyIds },
                    lastMessageAt = chatMessageRepository.findFirstByRoomIdOrderByIdDesc(room.id)?.createdAt,
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
        val members = QaMembers(memberRepository.findAllById(memberIds))

        return QaRoomView(
            roomId = room.id,
            sourceType = room.sourceType,
            status = room.status,
            opensAt = room.opensAt,
            expiresAt = room.expiresAt,
            endReason = room.endReason,
            members = roomMembers
                .sortedWith(compareBy({ it.memberId in dummyIds }, { it.memberId }))
                .map { roomMember ->
                    QaRoomMember(
                        member = members.of(roomMember.memberId),
                        dummy = roomMember.memberId in dummyIds,
                        left = roomMember.hasLeft,
                        lastReadMessageId = roomMember.lastReadMessageId,
                    )
                },
            messages = messages.map { message ->
                QaRoomMessage(
                    messageId = message.id,
                    sender = members.of(message.senderId),
                    fromDummy = message.senderId in dummyIds,
                    messageType = message.messageType,
                    content = message.content,
                    sentAt = message.createdAt,
                    unreadCount = message.unreadCountAmong(roomMembers),
                )
            },
        )
    }

    fun findLatestMessageId(roomId: Long): Long? = chatMessageRepository.findFirstByRoomIdOrderByIdDesc(roomId)?.id

    fun findActiveDummyIdsIn(roomId: Long): List<Long> {
        val dummyIds = qaDummies.findIds()
        return chatRoomMemberRepository.findByRoomId(roomId)
            .filter { !it.hasLeft && it.memberId in dummyIds }
            .map { it.memberId }
    }

    companion object {
        const val TIMELINE_SIZE = 50
    }
}
