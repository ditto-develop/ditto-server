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
import com.ditto.domain.chat.entity.ChatPeriod
import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.entity.ChatRoomMember
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.repository.ChatMessageRepository
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.infrastructure.storage.ObjectStorage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
@Transactional(readOnly = true)
class ChatService(
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val objectStorage: ObjectStorage,
    private val chatRoomAccessChecker: ChatRoomAccessChecker,
) {

    /**
     * 1:1 매칭 수락 시 두 회원의 채팅방을 생성한다. 이미 있으면 아무 것도 하지 않는다(멱등).
     * 매칭 수락 트랜잭션 안에서 호출된다.
     */
    @Transactional
    fun createPersonalRoom(personalMatchId: Long, memberAId: Long, memberBId: Long) {
        if (chatRoomRepository.existsBySourceTypeAndSourceId(ChatRoomType.PERSONAL, personalMatchId)) {
            return
        }

        val now = realNow()
        val room = chatRoomRepository.save(
            ChatRoom.personal(personalMatchId, ChatPeriod.weekendOf(now), now),
        )
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
        if (chatRoomRepository.existsBySourceTypeAndSourceId(ChatRoomType.GROUP, groupMatchId)) {
            return
        }

        val now = realNow()
        val room = chatRoomRepository.save(
            ChatRoom.group(groupMatchId, ChatPeriod.weekendOf(now), now),
        )
        chatRoomMemberRepository.saveAll(
            memberIds.map { ChatRoomMember.of(roomId = room.id, memberId = it) },
        )
    }

    /**
     * 성사된 재매칭의 채팅방을 예약한다. 이미 있으면 아무 것도 하지 않는다(멱등).
     * 방 예약 스케줄러가 쌍마다 부르며, 방과 참여자가 한 트랜잭션이어야 한다 —
     * 방만 남고 참여자가 없으면 아무도 들어갈 수 없는데, 예약 조회는 방 존재로 완료를 판정해
     * 다음 주기가 그 방을 고치지 않는다.
     *
     * 개방 시각은 [matchedAt]이 속한 주의 금요일이다. 성사가 이미 주말 도중이면 진행 중인 주말을
     * 돌려받아 방이 곧바로 `ACTIVE`가 된다(계획서 ⑧-1 "이미 시작된 그 주말에 합류").
     */
    @Transactional
    fun createRematchRoom(rematchId: Long, memberIds: List<Long>, matchedAt: LocalDateTime) {
        if (chatRoomRepository.existsBySourceTypeAndSourceId(ChatRoomType.REMATCH, rematchId)) {
            return
        }

        val room = chatRoomRepository.save(
            ChatRoom.rematch(rematchId, ChatPeriod.weekendOf(matchedAt), realNow()),
        )
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
        chatRoomAccessChecker.validateActiveMember(roomId, memberId)

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
        chatRoomAccessChecker.validateMember(roomId, memberId)

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
        chatRoomAccessChecker.validateActiveMember(roomId, senderId)
        val body = validateAndNormalizeContent(senderId, content, messageType)

        val message = chatMessageRepository.save(
            ChatMessage.of(roomId = roomId, senderId = senderId, content = body, messageType = messageType),
        )
        return toMessageResponse(message)
    }

    /**
     * 채팅 생명주기 시각은 어드민 시각 오버라이드를 따르지 않는다.
     *
     * opens_at·expires_at 은 **저장되는 값**이고, 이를 판정하는 만료 스케줄러는 실제 시각으로 돈다.
     * 생성만 가짜 시각을 쓰면 오버라이드가 과거 주일 때 방이 만들어지자마자 만료되고,
     * 미래 주면 며칠간 열리지 않는다 — 오버라이드를 꺼도 그 방은 망가진 채 남는다.
     */
    private fun realNow(): LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS)

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
            ?: throw chatRoomAccessChecker.notFoundOrForbidden(roomId)
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
