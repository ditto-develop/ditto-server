package com.ditto.api.chat.service

import com.ditto.api.chat.dto.ChatImageUploadUrlResponse
import com.ditto.api.chat.dto.ChatImageUploadUrlsRequest
import com.ditto.api.chat.dto.ChatImageUploadUrlsResponse
import com.ditto.api.chat.dto.ChatMessageResponse
import com.ditto.api.chat.dto.ChatMessagesResponse
import com.ditto.api.chat.dto.ChatRoomResponse
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.chat.entity.ChatMessage
import com.ditto.domain.chat.entity.ChatMessageType
import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.entity.ChatRoomMember
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.repository.ChatMessageRepository
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.infrastructure.storage.ObjectStorage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional(readOnly = true)
class ChatService(
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val objectStorage: ObjectStorage,
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

    /**
     * 그룹 매칭이 활성화(참가자 임계값 도달)될 때 참가자 전원의 채팅방을 생성한다.
     * 이미 있으면 아무 것도 하지 않는다(멱등). 그룹 참여 트랜잭션 안에서 호출된다.
     */
    @Transactional
    fun createGroupRoom(groupMatchId: Long, memberIds: List<Long>) {
        if (chatRoomRepository.existsByRoomTypeAndSourceId(ChatRoomType.GROUP, groupMatchId)) {
            return
        }

        val room = chatRoomRepository.save(ChatRoom.group(groupMatchId))
        chatRoomMemberRepository.saveAll(
            memberIds.map { ChatRoomMember.of(roomId = room.id, memberId = it) },
        )
    }

    /**
     * 이미지 업로드용 presigned PUT URL 발급. 방 멤버만 발급 가능하며, 크기·타입 검증 후 발급한다.
     * 발급받은 key(`chat/{memberId}/{uuid}`)로 업로드한 뒤 messageType=IMAGE, content=key 로 전송한다.
     */
    fun issueImageUploadUrls(
        memberId: Long,
        roomId: Long,
        request: ChatImageUploadUrlsRequest,
    ): ChatImageUploadUrlsResponse {
        validateRoomMember(roomId, memberId)

        val uploads = request.files.map { file ->
            if (file.contentLength > MAX_IMAGE_BYTES || !file.contentType.startsWith(IMAGE_CONTENT_TYPE_PREFIX)) {
                throw WarnException(ErrorCode.BAD_REQUEST)
            }
            val objectKey = imageKeyPrefix(memberId) + UUID.randomUUID()
            ChatImageUploadUrlResponse(
                objectKey = objectKey,
                uploadUrl = objectStorage.issueUploadUrl(objectKey, file.contentType, file.contentLength),
            )
        }
        return ChatImageUploadUrlsResponse(uploads = uploads)
    }

    /** 내 채팅방 목록 (상대 회원들 · 마지막 메시지 · 안읽음 수), 최근 대화순 */
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
            messages = messages.map { toMessageResponse(it) },
            nextCursor = nextCursor,
        )
    }

    /** 메시지 전송 — 멤버십·내용 검증 후 저장하고 저장된 메시지를 반환한다. (브로드캐스트는 STOMP 컨트롤러 책임) */
    @Transactional
    fun sendMessage(
        senderId: Long,
        roomId: Long,
        content: String,
        messageType: ChatMessageType = ChatMessageType.TEXT,
    ): ChatMessageResponse {
        validateRoomMember(roomId, senderId)
        val body = validateAndNormalizeContent(senderId, content, messageType)

        val message = chatMessageRepository.save(
            ChatMessage.of(roomId = roomId, senderId = senderId, content = body, messageType = messageType),
        )
        return toMessageResponse(message)
    }

    /** TEXT 는 공백·길이 검증, IMAGE 는 본인이 업로드한 key(chat/{senderId}/…)인지 검증한다. */
    private fun validateAndNormalizeContent(senderId: Long, content: String, messageType: ChatMessageType): String =
        when (messageType) {
            ChatMessageType.IMAGE -> {
                if (!content.startsWith(imageKeyPrefix(senderId)) || !objectStorage.exists(content)) {
                    throw WarnException(ErrorCode.INVALID_CHAT_IMAGE_KEY)
                }
                content
            }
            else -> {
                val trimmed = content.trim()
                if (trimmed.isEmpty() || trimmed.length > MAX_CONTENT_LENGTH) {
                    throw WarnException(ErrorCode.BAD_REQUEST)
                }
                trimmed
            }
        }

    /** 읽음 처리 — 내 last_read_message_id 를 전진시킨다. */
    @Transactional
    fun markAsRead(memberId: Long, roomId: Long, lastReadMessageId: Long) {
        val roomMember = chatRoomMemberRepository.findByRoomIdAndMemberId(roomId, memberId)
            ?: throw notFoundOrForbidden(roomId)
        roomMember.readUpTo(lastReadMessageId)
    }

    /** 저장된 메시지를 응답으로. IMAGE 는 content(S3 key)를 presigned GET URL 로 해석해 imageUrl 에 담는다. */
    fun toMessageResponse(message: ChatMessage): ChatMessageResponse {
        val imageUrl = if (message.messageType == ChatMessageType.IMAGE) {
            objectStorage.issueViewUrl(message.content)
        } else {
            null
        }
        return ChatMessageResponse.of(message, imageUrl)
    }

    private fun toRoomResponse(
        room: ChatRoom,
        myRoomMember: ChatRoomMember,
        roomMembers: List<ChatRoomMember>,
        memberId: Long,
    ): ChatRoomResponse {
        val counterpartMemberIds = roomMembers.filter { it.memberId != memberId }.map { it.memberId }
        val lastMessage = chatMessageRepository.findFirstByRoomIdOrderByIdDesc(room.id)
        return ChatRoomResponse.of(
            room = room,
            counterpartMemberIds = counterpartMemberIds,
            lastMessage = lastMessage?.let { toMessageResponse(it) },
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
        private const val MAX_CONTENT_LENGTH = 1000
        private const val MAX_IMAGE_BYTES = 10L * 1024 * 1024 // 10MB
        private const val IMAGE_CONTENT_TYPE_PREFIX = "image/"
        private const val IMAGE_KEY_ROOT = "chat"

        /** 이미지 key 접두사 — 소유 검증(본인이 발급받은 key만 전송 가능)에 쓰인다. */
        fun imageKeyPrefix(memberId: Long): String = "$IMAGE_KEY_ROOT/$memberId/"
    }
}
