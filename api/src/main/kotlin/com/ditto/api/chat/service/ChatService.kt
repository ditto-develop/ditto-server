package com.ditto.api.chat.service

import com.ditto.api.chat.dto.ChatImageUploadUrlResponse
import com.ditto.api.chat.dto.ChatImageUploadUrlsRequest
import com.ditto.api.chat.dto.ChatImageUploadUrlsResponse
import com.ditto.api.chat.dto.ChatMessageResponse
import com.ditto.api.chat.dto.ChatMessagesResponse
import com.ditto.api.chat.dto.ChatReadEvent
import com.ditto.api.chat.dto.ChatRoomResponse
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.chat.entity.ChatMessage
import com.ditto.domain.chat.entity.ChatMessageType
import com.ditto.domain.chat.entity.ChatPeriod
import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.entity.ChatRoomMember
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
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
    private val groupMatchRepository: GroupMatchRepository,
    private val quizSetRepository: QuizSetRepository,
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
     *
     * @return 만들어진(또는 이미 있던) 방 ID. 그룹 구성 알림이 눌렀을 때 열 방을 가리켜야 해서 돌려준다.
     */
    @Transactional
    fun createGroupRoom(groupMatchId: Long, memberIds: List<Long>): Long {
        chatRoomRepository.findBySourceTypeAndSourceId(ChatRoomType.GROUP, groupMatchId)
            ?.let { return it.id }

        val now = realNow()
        val room = chatRoomRepository.save(
            ChatRoom.group(groupMatchId, ChatPeriod.weekendOf(now), now),
        )
        chatRoomMemberRepository.saveAll(
            memberIds.map { ChatRoomMember.of(roomId = room.id, memberId = it) },
        )
        return room.id
    }

    /**
     * 이미 열린 그룹 채팅방에 구성원 한 명을 넣는다. 이미 들어가 있으면 아무 것도 하지 않는다(멱등).
     *
     * 그룹 정원이 성사 최소 인원보다 커서 **방이 열린 뒤에도 수락이 더 들어온다.**
     * [createGroupRoom]은 방이 있으면 곧바로 돌아가므로 그 경로로는 늦은 수락자가 방에 들어가지 못한다.
     *
     * @return 그 그룹의 채팅방 ID. 방이 아직 없으면 null — 성사 전에는 부를 일이 없다.
     */
    @Transactional
    fun addGroupRoomMember(groupMatchId: Long, memberId: Long): Long? {
        val room = chatRoomRepository.findBySourceTypeAndSourceId(ChatRoomType.GROUP, groupMatchId) ?: return null

        if (!chatRoomMemberRepository.existsByRoomIdAndMemberId(room.id, memberId)) {
            chatRoomMemberRepository.save(ChatRoomMember.of(roomId = room.id, memberId = memberId))
        }
        return room.id
    }

    /**
     * 성사된 재매칭의 채팅방을 예약한다. 이미 있으면 아무 것도 하지 않는다(멱등).
     * 방 예약 스케줄러가 쌍마다 부르며, 방과 참여자가 한 트랜잭션이어야 한다 —
     * 방만 남고 참여자가 없으면 아무도 들어갈 수 없는데, 예약 조회는 방 존재로 완료를 판정해
     * 다음 주기가 그 방을 고치지 않는다.
     *
     * 어느 주말에 열지는 예약하는 쪽이 정해 [weekend]로 넘긴다 — 재매칭은 "성사 이후 처음 오는
     * 금요일"이라는 자기 규칙을 쓰고(`RematchChatRoomOpener`), 일반 매칭 방과 다르다.
     *
     * @return 예약된(또는 이미 있던) 방 ID. 재매칭 성사 알림이 그 방을 가리켜야 해서 돌려준다.
     */
    @Transactional
    fun createRematchRoom(rematchId: Long, memberIds: List<Long>, weekend: ChatPeriod): Long {
        chatRoomRepository.findBySourceTypeAndSourceId(ChatRoomType.REMATCH, rematchId)
            ?.let { return it.id }

        val room = chatRoomRepository.save(ChatRoom.rematch(rematchId, weekend, realNow()))
        chatRoomMemberRepository.saveAll(
            memberIds.map { ChatRoomMember.of(roomId = room.id, memberId = it) },
        )
        return room.id
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

    /**
     * 내 채팅방 목록 (상대 회원들 · 마지막 메시지 · 안읽음 수), 최근 대화순.
     *
     * **내가 나간 방은 빼고 준다.** 나간 사람에게는 그 방이 없는 것으로 다루기 때문이다
     * (`ChatRoomAccessChecker.validateMember` 가 같은 기준으로 조회도 막는다 — 이슈 #196).
     * 종료된 방은 계속 준다: 내가 나간 것이 아니라 방이 끝난 것이라 지난 대화를 볼 수 있다.
     * 내가 감춘 방([hideRoom])도 뺀다. 목록에서만 빠지고 조회는 그대로 된다.
     */
    fun getMyRooms(memberId: Long): List<ChatRoomResponse> {
        val myRoomMembers = chatRoomMemberRepository.findByMemberId(memberId)
            .filter { !it.hasLeft && !it.isHidden }
        if (myRoomMembers.isEmpty()) {
            return emptyList()
        }

        val roomIds = myRoomMembers.map { it.roomId }
        val roomsById = chatRoomRepository.findAllById(roomIds).associateBy { it.id }
        // 방별 참여자 (상대 회원 파악용). 방 수가 늘면 마지막 메시지/안읽음 집계를 일괄 조회로 최적화 대상.
        val membersByRoomId = chatRoomMemberRepository.findByRoomIdIn(roomIds).groupBy { it.roomId }
        val roomNameByRoomId = groupRoomNames(roomsById.values)

        return myRoomMembers
            .mapNotNull { roomMember ->
                val room = roomsById[roomMember.roomId] ?: return@mapNotNull null
                toRoomResponse(
                    room,
                    roomMember,
                    membersByRoomId[room.id].orEmpty(),
                    memberId,
                    roomNameByRoomId[room.id],
                )
            }
            .sortedByDescending { it.lastMessage?.createdAt ?: it.createdAt }
    }

    /**
     * 종료된 방을 내 목록에서만 감춘다. 상대 목록·메시지는 그대로고, 다시 요청해도 성공한다(멱등).
     *
     * 끝난 방만 허용한다. 진행 중인 방을 감추면 새 메시지가 와도 목록에 다시 뜨지 않아 대화를 놓친다.
     * 감춘 방도 roomId 로는 그대로 조회된다. 끝난 방에는 새 대화가 쌓이지 않아 이탈(#196)처럼
     * 방 전체를 가릴 이유가 없다.
     */
    @Transactional
    fun hideRoom(memberId: Long, roomId: Long) {
        val roomMember = chatRoomMemberRepository.findByRoomIdAndMemberId(roomId, memberId)
            ?: throw chatRoomAccessChecker.notFoundOrForbidden(roomId)
        // 나간 사람에게는 방이 없는 것으로 다룬다(validateMember 와 같은 기준).
        if (roomMember.hasLeft) {
            throw chatRoomAccessChecker.notFoundOrForbidden(roomId)
        }

        val room = chatRoomRepository.findById(roomId).orElseThrow { chatRoomAccessChecker.notFoundOrForbidden(roomId) }
        if (!room.isEnded) {
            throw WarnException(ErrorCode.CHAT_ROOM_NOT_ENDED)
        }

        roomMember.hide(realNow())
    }

    /** 방의 과거 메시지 커서 페이징 (최신순). cursor 미만(더 과거)으로 size 개. */
    fun getMessages(memberId: Long, roomId: Long, cursor: Long?, size: Int): ChatMessagesResponse {
        chatRoomAccessChecker.validateMember(roomId, memberId)

        val pageSize = size.coerceIn(1, MAX_PAGE_SIZE)
        val messages = chatMessageRepository.findByRoomIdWithCursor(roomId, cursor, pageSize)
        val nextCursor = if (messages.size == pageSize) messages.last().id else null
        val roomMembers = chatRoomMemberRepository.findByRoomId(roomId)

        return ChatMessagesResponse(
            messages = messages.map { toMessageResponse(it, roomMembers) },
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
        return toMessageResponse(message, chatRoomMemberRepository.findByRoomId(roomId))
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

    /**
     * 읽음 처리. 커서가 실제로 전진했을 때만 브로드캐스트용 이벤트를 돌려준다(재시도·후진은 null).
     * 이탈자는 커서만 기록하고 이벤트는 내지 않는다. unreadCount 에 세지 않는 사람이라 이벤트가 나가면 상대가 하나 더 뺀다.
     */
    @Transactional
    fun markAsRead(memberId: Long, roomId: Long, lastReadMessageId: Long): ChatReadEvent? {
        val roomMember = chatRoomMemberRepository.findWithLockByRoomIdAndMemberId(roomId, memberId)
            ?: throw chatRoomAccessChecker.notFoundOrForbidden(roomId)
        if (!chatMessageRepository.existsByIdAndRoomId(lastReadMessageId, roomId)) {
            throw WarnException(ErrorCode.BAD_REQUEST, "이 방의 메시지가 아닙니다: messageId=$lastReadMessageId")
        }
        val previousLastReadMessageId = roomMember.lastReadMessageId
        if (!roomMember.readUpTo(lastReadMessageId) || roomMember.hasLeft) {
            return null
        }
        return ChatReadEvent(
            roomId = roomId,
            memberId = memberId,
            previousLastReadMessageId = previousLastReadMessageId,
            lastReadMessageId = lastReadMessageId,
        )
    }

    /** 저장된 메시지를 응답으로. IMAGE 는 content(S3 key)를 presigned GET URL 로 해석해 imageUrl 에 담는다. */
    private fun toMessageResponse(message: ChatMessage, roomMembers: Collection<ChatRoomMember>): ChatMessageResponse {
        val imageUrl = if (message.messageType == ChatMessageType.IMAGE) {
            objectStorage.issueViewUrl(message.content)
        } else {
            null
        }
        return ChatMessageResponse.of(message, imageUrl, roomMembers)
    }

    /**
     * 그룹 방의 기본 이름(= 그룹 퀴즈 주제)을 방 ID 로 모아 온다.
     *
     * 그룹 방은 `sourceId` 가 `groupMatchId` 이고 거기서 `quizSetId` 로 이어진다.
     * 방마다 따로 타면 N+1 이라 두 번의 일괄 조회로 끝낸다.
     */
    private fun groupRoomNames(rooms: Collection<ChatRoom>): Map<Long, String> {
        val groupRooms = rooms.filter { it.sourceType == ChatRoomType.GROUP }
        if (groupRooms.isEmpty()) return emptyMap()

        val quizSetIdByMatchId = groupMatchRepository
            .findAllById(groupRooms.map { it.sourceId })
            .associate { it.id to it.quizSetId }
        val titleByQuizSetId = quizSetRepository
            .findAllById(quizSetIdByMatchId.values.toSet())
            .associate { it.id to it.title }

        return groupRooms.mapNotNull { room ->
            val quizSetId = quizSetIdByMatchId[room.sourceId] ?: return@mapNotNull null
            val title = titleByQuizSetId[quizSetId] ?: return@mapNotNull null
            room.id to title
        }.toMap()
    }

    private fun toRoomResponse(
        room: ChatRoom,
        myRoomMember: ChatRoomMember,
        roomMembers: List<ChatRoomMember>,
        memberId: Long,
        roomName: String?,
    ): ChatRoomResponse {
        // 이탈자는 상대 목록에서 뺀다 — FE 는 이 목록으로 "지금 함께 있는 사람"을 그린다.
        val counterpartMemberIds = roomMembers
            .filter { it.memberId != memberId && !it.hasLeft }
            .map { it.memberId }
        val lastMessage = chatMessageRepository.findFirstByRoomIdOrderByIdDesc(room.id)
        return ChatRoomResponse.of(
            room = room,
            counterpartMemberIds = counterpartMemberIds,
            lastMessage = lastMessage?.let { toMessageResponse(it, roomMembers) },
            unreadCount = unreadCount(room.id, myRoomMember.lastReadMessageId),
            hasLeft = myRoomMember.hasLeft,
            roomName = roomName,
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
