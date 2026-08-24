package com.ditto.api.chat

import com.ditto.api.chat.dto.ChatImageUploadFileRequest
import com.ditto.api.chat.dto.ChatImageUploadUrlsRequest
import com.ditto.api.chat.service.ChatService
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.chat.ChatRoomFixture
import com.ditto.domain.chat.entity.ChatMessage
import com.ditto.domain.chat.entity.ChatMessageType
import com.ditto.domain.chat.entity.ChatRoomMember
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.repository.ChatMessageRepository
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDateTime
import javax.sql.DataSource

/** 개방된 주말 한가운데. 이 시각으로 만든 방은 곧바로 ACTIVE 다. */
private val FRIDAY = LocalDateTime.of(2026, 3, 13, 12, 0)

class ChatServiceTest(
    private val chatService: ChatService,
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val chatMessageRepository: ChatMessageRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    /**
     * 대화가 가능한(개방된) 1:1 방. `createPersonalRoom`은 실제 시각으로 기간을 잡으므로 평일에 돌리면
     * `SCHEDULED`가 되어 전송·구독·이미지 발급이 막힌다 — 대화 경로를 검증하려면 개방된 방이 필요하다.
     */
    fun saveOpenedRoom(sourceId: Long = 100L, vararg memberIds: Long) =
        chatRoomRepository.save(ChatRoomFixture.personal(sourceId = sourceId, now = FRIDAY)).also { room ->
            chatRoomMemberRepository.saveAll(memberIds.map { ChatRoomMember.of(roomId = room.id, memberId = it) })
        }

    "1:1 방을 생성하면 두 회원의 멤버 레코드가 함께 생성된다" {
        // when
        chatService.createPersonalRoom(personalMatchId = 100L, memberAId = 1L, memberBId = 2L)

        // then
        val room = chatRoomRepository.findBySourceTypeAndSourceId(ChatRoomType.PERSONAL, 100L)
        room shouldNotBe null
        chatRoomMemberRepository.findByRoomIdIn(listOf(room!!.id))
            .map { it.memberId }.toSet() shouldBe setOf(1L, 2L)
    }

    "이미 방이 있으면 다시 생성해도 방이 하나만 유지된다(멱등)" {
        // when
        chatService.createPersonalRoom(personalMatchId = 100L, memberAId = 1L, memberBId = 2L)
        chatService.createPersonalRoom(personalMatchId = 100L, memberAId = 1L, memberBId = 2L)

        // then
        chatRoomRepository.findAll().count { it.sourceId == 100L } shouldBe 1
    }

    "그룹 방을 생성하면 참가자 전원의 멤버 레코드가 GROUP 방에 생성된다" {
        // when
        chatService.createGroupRoom(groupMatchId = 200L, memberIds = listOf(1L, 2L, 3L))

        // then
        val room = chatRoomRepository.findBySourceTypeAndSourceId(ChatRoomType.GROUP, 200L)
        room shouldNotBe null
        chatRoomMemberRepository.findByRoomIdIn(listOf(room!!.id))
            .map { it.memberId }.toSet() shouldBe setOf(1L, 2L, 3L)
    }

    "이미 그룹 방이 있으면 다시 생성해도 방이 하나만 유지된다(멱등)" {
        // when
        chatService.createGroupRoom(groupMatchId = 200L, memberIds = listOf(1L, 2L, 3L))
        chatService.createGroupRoom(groupMatchId = 200L, memberIds = listOf(1L, 2L, 3L))

        // then
        chatRoomRepository.findAll().count { it.sourceId == 200L } shouldBe 1
    }

    "내 채팅방 목록은 상대 회원·마지막 메시지·안읽음 수를 담아 반환한다" {
        // given: 방 + 나(1)/상대(2), 메시지 3개, 첫 메시지까지 읽음
        val room = saveOpenedRoom(100L, 1L, 2L)
        val first = chatMessageRepository.save(ChatMessage.of(room.id, 2L, "첫 메시지"))
        chatMessageRepository.save(ChatMessage.of(room.id, 2L, "둘째 메시지"))
        val last = chatMessageRepository.save(ChatMessage.of(room.id, 1L, "셋째 메시지"))
        chatService.markAsRead(memberId = 1L, roomId = room.id, lastReadMessageId = first.id)

        // when
        val rooms = chatService.getMyRooms(memberId = 1L)

        // then
        rooms.size shouldBe 1
        val response = rooms[0]
        response.counterpartMemberIds shouldBe listOf(2L)
        response.lastMessage?.id shouldBe last.id
        response.unreadCount shouldBe 2L
        response.hasLeft shouldBe false
    }

    "이탈한 상대는 counterpartMemberIds 에서 빠지고, 내가 나간 방은 hasLeft 로 표시된다" {
        // given: 3명 방에서 3L 이 이탈
        val room = saveOpenedRoom(100L, 1L, 2L, 3L)
        chatRoomMemberRepository.findByRoomIdAndMemberId(room.id, 3L)
            ?.apply { leave(FRIDAY) }
            ?.let { chatRoomMemberRepository.save(it) }

        // then: 남은 사람 화면에서 이탈자가 상대 목록에서 빠진다
        chatService.getMyRooms(memberId = 1L)[0].counterpartMemberIds shouldBe listOf(2L)
        // then: 이탈자 화면에서는 방이 목록에 남되 hasLeft 로 구분된다 (읽기 전용 안내용)
        chatService.getMyRooms(memberId = 3L)[0].hasLeft shouldBe true
    }

    "메시지 조회는 최신순으로 size 만큼 반환하고 다음 커서를 준다" {
        // given
        val room = saveOpenedRoom(100L, 1L, 2L)
        val saved = (1..5).map { chatMessageRepository.save(ChatMessage.of(room.id, 1L, "메시지 $it")) }

        // when: 최신 2개
        val firstPage = chatService.getMessages(memberId = 1L, roomId = room.id, cursor = null, size = 2)

        // then
        firstPage.messages.map { it.id } shouldBe listOf(saved[4].id, saved[3].id)
        firstPage.nextCursor shouldBe saved[3].id

        // when: 커서로 위로 스크롤
        val secondPage = chatService.getMessages(memberId = 1L, roomId = room.id, cursor = firstPage.nextCursor, size = 2)

        // then
        secondPage.messages.map { it.id } shouldBe listOf(saved[2].id, saved[1].id)
    }

    "방 참여자가 아니면 메시지 조회 시 NOT_CHAT_ROOM_MEMBER 예외가 발생한다" {
        // given
        val room = saveOpenedRoom(100L, 1L, 2L)

        // when & then
        shouldThrow<WarnException> {
            chatService.getMessages(memberId = 99L, roomId = room.id, cursor = null, size = 30)
        }.errorCode shouldBe ErrorCode.NOT_CHAT_ROOM_MEMBER
    }

    "존재하지 않는 방을 조회하면 CHAT_ROOM_NOT_FOUND 예외가 발생한다" {
        // when & then
        shouldThrow<WarnException> {
            chatService.getMessages(memberId = 1L, roomId = 9999L, cursor = null, size = 30)
        }.errorCode shouldBe ErrorCode.CHAT_ROOM_NOT_FOUND
    }

    "읽음 처리는 last_read_message_id 를 전진시키고 뒤로 가지 않는다" {
        // given
        val room = saveOpenedRoom(100L, 1L, 2L)
        val messages = (1..3).map { chatMessageRepository.save(ChatMessage.of(room.id, 2L, "메시지 $it")) }

        // when: 3번째까지 읽고, 다시 1번째로 되돌리려 시도
        chatService.markAsRead(memberId = 1L, roomId = room.id, lastReadMessageId = messages[2].id)
        chatService.markAsRead(memberId = 1L, roomId = room.id, lastReadMessageId = messages[0].id)

        // then: 앞으로만 전진, 3번째 유지
        val roomMember = chatRoomMemberRepository.findByRoomIdAndMemberId(room.id, 1L)!!
        roomMember.lastReadMessageId shouldBe messages[2].id
    }

    "이미지 업로드 URL은 방 멤버에게 내 소유 접두사(chat/{memberId}/) 키로 발급된다" {
        // given
        val room = saveOpenedRoom(100L, 1L, 2L)
        val request = ChatImageUploadUrlsRequest(
            files = listOf(ChatImageUploadFileRequest(contentType = "image/jpeg", contentLength = 1024)),
        )

        // when
        val result = chatService.issueImageUploadUrls(memberId = 1L, roomId = room.id, request = request)

        // then
        result.uploads.size shouldBe 1
        result.uploads[0].objectKey.startsWith("chat/1/") shouldBe true
        result.uploads[0].uploadUrl.isNotBlank() shouldBe true
    }

    "방 참여자가 아니면 이미지 업로드 URL 발급 시 NOT_CHAT_ROOM_MEMBER 예외가 발생한다" {
        // given
        val room = saveOpenedRoom(100L, 1L, 2L)
        val request = ChatImageUploadUrlsRequest(
            files = listOf(ChatImageUploadFileRequest(contentType = "image/jpeg", contentLength = 1024)),
        )

        // when & then
        shouldThrow<WarnException> {
            chatService.issueImageUploadUrls(memberId = 99L, roomId = room.id, request = request)
        }.errorCode shouldBe ErrorCode.NOT_CHAT_ROOM_MEMBER
    }

    "이미지가 아닌 contentType 이면 BAD_REQUEST 예외가 발생한다" {
        // given
        val room = saveOpenedRoom(100L, 1L, 2L)
        val request = ChatImageUploadUrlsRequest(
            files = listOf(ChatImageUploadFileRequest(contentType = "application/pdf", contentLength = 1024)),
        )

        // when & then
        shouldThrow<WarnException> {
            chatService.issueImageUploadUrls(memberId = 1L, roomId = room.id, request = request)
        }.errorCode shouldBe ErrorCode.BAD_REQUEST
    }

    "IMAGE 메시지는 조회 시 content(키)가 presigned GET URL 로 해석되어 imageUrl 에 담긴다" {
        // given
        val room = saveOpenedRoom(100L, 1L, 2L)
        chatMessageRepository.save(
            ChatMessage.of(room.id, 1L, "chat/1/img-key", ChatMessageType.IMAGE),
        )

        // when
        val page = chatService.getMessages(memberId = 1L, roomId = room.id, cursor = null, size = 30)

        // then
        val message = page.messages[0]
        message.messageType shouldBe ChatMessageType.IMAGE
        message.imageUrl shouldNotBe null
    }

    "메시지를 보내면 저장되고 저장된 메시지를 반환한다" {
        // given
        val room = saveOpenedRoom(100L, 1L, 2L)

        // when
        val sent = chatService.sendMessage(senderId = 1L, roomId = room.id, content = "  안녕하세요  ")

        // then
        sent.senderId shouldBe 1L
        sent.content shouldBe "안녕하세요" // trim 됨
        chatMessageRepository.countByRoomId(room.id) shouldBe 1L
    }

    "방 참여자가 아니면 전송 시 NOT_CHAT_ROOM_MEMBER 예외가 발생한다" {
        // given
        val room = saveOpenedRoom(100L, 1L, 2L)

        // when & then
        shouldThrow<WarnException> {
            chatService.sendMessage(senderId = 99L, roomId = room.id, content = "안녕")
        }.errorCode shouldBe ErrorCode.NOT_CHAT_ROOM_MEMBER
    }

    "빈 내용을 보내면 BAD_REQUEST 예외가 발생한다" {
        // given
        val room = saveOpenedRoom(100L, 1L, 2L)

        // when & then
        shouldThrow<WarnException> {
            chatService.sendMessage(senderId = 1L, roomId = room.id, content = "   ")
        }.errorCode shouldBe ErrorCode.BAD_REQUEST
    }

    "IMAGE 전송은 내가 업로드한 key 로만 가능하고, 저장 후 imageUrl 이 해석된다" {
        // given: 방 + 업로드 URL 발급(FakeObjectStorage 는 발급한 key 를 업로드된 것으로 간주)
        val room = saveOpenedRoom(100L, 1L, 2L)
        val issued = chatService.issueImageUploadUrls(
            memberId = 1L,
            roomId = room.id,
            request = ChatImageUploadUrlsRequest(
                files = listOf(ChatImageUploadFileRequest(contentType = "image/png", contentLength = 2048)),
            ),
        )
        val key = issued.uploads[0].objectKey

        // when
        val sent = chatService.sendMessage(
            senderId = 1L, roomId = room.id, content = key, messageType = ChatMessageType.IMAGE,
        )

        // then
        sent.messageType shouldBe ChatMessageType.IMAGE
        sent.content shouldBe key
        sent.imageUrl shouldNotBe null
    }

    "업로드하지 않은(내 소유가 아닌) key 로 IMAGE 전송하면 INVALID_CHAT_IMAGE_KEY 예외가 발생한다" {
        // given
        val room = saveOpenedRoom(100L, 1L, 2L)

        // when & then
        shouldThrow<WarnException> {
            chatService.sendMessage(
                senderId = 1L, roomId = room.id, content = "chat/1/not-uploaded", messageType = ChatMessageType.IMAGE,
            )
        }.errorCode shouldBe ErrorCode.INVALID_CHAT_IMAGE_KEY
    }
})
