package com.ditto.api.chat.service

import com.ditto.api.chat.dto.ChatMessageResponse
import com.ditto.api.chat.dto.ChatMessagesResponse
import com.ditto.api.chat.dto.ChatRoomResponse
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.entity.ChatRoomMember
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.repository.ChatMessageRepository
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ChatService(
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val chatMessageRepository: ChatMessageRepository,
) {

    /**
     * 1:1 매칭 수락 시 두 회원의 채팅방을 생성한다. 이미 있으면 아무 것도 하지 않는다(멱등).
     * 매칭 수락 트랜잭션 안에서 호출된다.
     */
    @Transactional
    fun createPersonalRoom(personalMatchId: Long, memberAId: Long, memberBId: Long) {
        if (chatRoomRepository.existsByRoomTypeAndSourceId(ChatRoomType.PERSONAL, personalMatchId)) {
            return
        }

        val room = chatRoomRepository.save(ChatRoom.personal(personalMatchId))
        chatRoomMemberRepository.saveAll(
            listOf(
                ChatRoomMember.of(roomId = room.id, memberId = memberAId),
                ChatRoomMember.of(roomId = room.id, memberId = memberBId),
            ),
        )
    }

    /** 내 채팅방 목록 (상대 회원 · 마지막 메시지 · 안읽음 수), 최근 대화순 */
    fun getMyRooms(memberId: Long): List<ChatRoomResponse> {
        val myRoomMembers = chatRoomMemberRepository.findByMemberId(memberId)
        if (myRoomMembers.isEmpty()) {
            return emptyList()
        }

        val roomIds = myRoomMembers.map { it.roomId }
        val roomsById = chatRoomRepository.findAllById(roomIds).associateBy { it.id }
        // 방별 참여자 (상대 회원 파악용). 방 수가 늘면 마지막 메시지/안읽음 집계를 일괄 조회로 최적화 대상.
        val membersByRoomId = chatRoomMemberRepository.findByRoomIdIn(roomIds).groupBy { it.roomId }

        return myRoomMembers
            .mapNotNull { roomMember ->
                val room = roomsById[roomMember.roomId] ?: return@mapNotNull null
                toRoomResponse(room, roomMember, membersByRoomId[room.id].orEmpty(), memberId)
            }
            .sortedByDescending { it.lastMessage?.createdAt ?: it.createdAt }
    }

    /** 방의 과거 메시지 커서 페이징 (최신순). cursor 미만(더 과거)으로 size 개. */
    fun getMessages(memberId: Long, roomId: Long, cursor: Long?, size: Int): ChatMessagesResponse {
        validateRoomMember(roomId, memberId)

        val pageSize = size.coerceIn(1, MAX_PAGE_SIZE)
        val messages = chatMessageRepository.findByRoomIdWithCursor(roomId, cursor, pageSize)
        val nextCursor = if (messages.size == pageSize) messages.last().id else null

        return ChatMessagesResponse(
            messages = messages.map { ChatMessageResponse.from(it) },
            nextCursor = nextCursor,
        )
    }

    /** 읽음 처리 — 내 last_read_message_id 를 전진시킨다. */
    @Transactional
    fun markAsRead(memberId: Long, roomId: Long, lastReadMessageId: Long) {
        val roomMember = chatRoomMemberRepository.findByRoomIdAndMemberId(roomId, memberId)
            ?: throw notFoundOrForbidden(roomId)
        roomMember.readUpTo(lastReadMessageId)
    }

    private fun toRoomResponse(
        room: ChatRoom,
        myRoomMember: ChatRoomMember,
        roomMembers: List<ChatRoomMember>,
        memberId: Long,
    ): ChatRoomResponse {
        val counterpartId = roomMembers.firstOrNull { it.memberId != memberId }?.memberId
        val lastMessage = chatMessageRepository.findFirstByRoomIdOrderByIdDesc(room.id)
        return ChatRoomResponse.of(
            room = room,
            counterpartMemberId = counterpartId,
            lastMessage = lastMessage?.let { ChatMessageResponse.from(it) },
            unreadCount = unreadCount(room.id, myRoomMember.lastReadMessageId),
        )
    }

    private fun unreadCount(roomId: Long, lastReadMessageId: Long?): Long =
        if (lastReadMessageId == null) {
            chatMessageRepository.countByRoomId(roomId)
        } else {
            chatMessageRepository.countByRoomIdAndIdGreaterThan(roomId, lastReadMessageId)
        }

    private fun validateRoomMember(roomId: Long, memberId: Long) {
        if (!chatRoomMemberRepository.existsByRoomIdAndMemberId(roomId, memberId)) {
            throw notFoundOrForbidden(roomId)
        }
    }

    private fun notFoundOrForbidden(roomId: Long): WarnException =
        if (chatRoomRepository.existsById(roomId)) {
            WarnException(ErrorCode.NOT_CHAT_ROOM_MEMBER)
        } else {
            WarnException(ErrorCode.CHAT_ROOM_NOT_FOUND)
        }

    companion object {
        private const val MAX_PAGE_SIZE = 100
    }
}
