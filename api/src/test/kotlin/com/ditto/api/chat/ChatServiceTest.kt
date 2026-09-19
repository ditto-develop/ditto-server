package com.ditto.api.chat

import com.ditto.api.chat.dto.ChatImageUploadFileRequest
import com.ditto.api.chat.dto.ChatImageUploadUrlsRequest
import com.ditto.api.chat.dto.ChatReadEvent
import com.ditto.api.chat.service.ChatService
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.match.GroupMatchFixture
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
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import javax.sql.DataSource

/** 개방된 주말 한가운데. 이 시각으로 만든 방은 곧바로 ACTIVE 다. */
private val FRIDAY = LocalDateTime.of(2026, 3, 13, 12, 0)

class ChatServiceTest(
    private val chatService: ChatService,
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val groupMatchRepository: GroupMatchRepository,
    private val quizSetRepository: QuizSetRepository,
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

    /** 만료로 끝난 방. */
    fun saveEndedRoom(sourceId: Long = 100L, vararg memberIds: Long) =
        saveOpenedRoom(sourceId, *memberIds).also { room ->
            room.expire(FRIDAY.plusDays(3))
            chatRoomRepository.save(room)
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
        // 1:1 방은 이름을 두지 않는다 — 상대가 한 명이라 화면이 닉네임으로 그린다.
        response.roomName shouldBe null
    }

    // 방 이름 필드가 없어 화면이 참여자 닉네임을 이어 붙이고 있었다(QA BUG-070).
    "그룹 방 목록은 그룹 퀴즈 주제를 기본 이름으로 담는다" {
        val quizSet = quizSetRepository.save(QuizSetFixture.create(title = "주말 취미 퀴즈"))
        val groupMatch = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = quizSet.id))
        chatService.createGroupRoom(groupMatchId = groupMatch.id, memberIds = listOf(1L, 2L, 3L))

        val rooms = chatService.getMyRooms(memberId = 1L)

        rooms.size shouldBe 1
        rooms[0].roomName shouldBe "주말 취미 퀴즈"
    }

    "이탈한 상대는 counterpartMemberIds 에서 빠지고, 내가 나간 방은 목록에서 사라진다" {
        // given: 3명 방에서 3L 이 이탈
        val room = saveOpenedRoom(100L, 1L, 2L, 3L)
        chatRoomMemberRepository.findByRoomIdAndMemberId(room.id, 3L)
            ?.apply { leave(FRIDAY) }
            ?.let { chatRoomMemberRepository.save(it) }

        // then: 남은 사람 화면에서 이탈자가 상대 목록에서 빠진다
        chatService.getMyRooms(memberId = 1L)[0].counterpartMemberIds shouldBe listOf(2L)
        // then: 이탈자에게는 그 방이 아예 없다 — 읽기 전용으로 남기던 정책을 철회했다(#196)
        chatService.getMyRooms(memberId = 3L).map { it.roomId } shouldNotContain room.id
    }

    "종료된 방을 숨기면 내 목록에서만 빠지고 상대 목록에는 남는다" {
        // given: 종료된 방
        val room = saveEndedRoom(100L, 1L, 2L)

        // when
        chatService.hideRoom(memberId = 1L, roomId = room.id)

        // then
        chatService.getMyRooms(memberId = 1L).map { it.roomId } shouldNotContain room.id
        chatService.getMyRooms(memberId = 2L).map { it.roomId } shouldContain room.id
    }

    "숨긴 방도 메시지는 그대로 조회된다" {
        val room = saveEndedRoom(100L, 1L, 2L)
        chatMessageRepository.save(ChatMessage.of(room.id, 2L, "지난 대화"))
        chatService.hideRoom(memberId = 1L, roomId = room.id)

        chatService.getMessages(memberId = 1L, roomId = room.id, cursor = null, size = 20)
            .messages.map { it.content } shouldBe listOf("지난 대화")
    }

    "이미 숨긴 방을 다시 숨겨도 성공한다(멱등)" {
        val room = saveEndedRoom(100L, 1L, 2L)
        chatService.hideRoom(memberId = 1L, roomId = room.id)

        chatService.hideRoom(memberId = 1L, roomId = room.id)

        chatService.getMyRooms(memberId = 1L).map { it.roomId } shouldNotContain room.id
    }

    "진행 중인 방은 숨길 수 없다" {
        val room = saveOpenedRoom(100L, 1L, 2L)

        shouldThrow<WarnException> {
            chatService.hideRoom(memberId = 1L, roomId = room.id)
        }.errorCode shouldBe ErrorCode.CHAT_ROOM_NOT_ENDED
    }

    "방 멤버가 아니면 숨길 수 없다" {
        val room = saveEndedRoom(100L, 1L, 2L)

        shouldThrow<WarnException> {
            chatService.hideRoom(memberId = 99L, roomId = room.id)
        }.errorCode shouldBe ErrorCode.NOT_CHAT_ROOM_MEMBER
    }

    "나간 방은 숨길 수 없다" {
        val room = saveEndedRoom(100L, 1L, 2L, 3L)
        chatRoomMemberRepository.findByRoomIdAndMemberId(room.id, 3L)
            ?.apply { leave(FRIDAY) }
            ?.let { chatRoomMemberRepository.save(it) }

        shouldThrow<WarnException> {
            chatService.hideRoom(memberId = 3L, roomId = room.id)
        }.errorCode shouldBe ErrorCode.NOT_CHAT_ROOM_MEMBER
    }

    "나간 방은 메시지도 읽을 수 없다 — 나간 뒤 오간 대화까지 읽히던 구멍을 막는다" {
        val room = saveOpenedRoom(100L, 1L, 2L, 3L)
        chatRoomMemberRepository.findByRoomIdAndMemberId(room.id, 3L)
            ?.apply { leave(FRIDAY) }
            ?.let { chatRoomMemberRepository.save(it) }
        // 나간 뒤 남은 사람들이 나눈 대화
        chatMessageRepository.save(ChatMessage.of(room.id, 1L, "나간 뒤 대화"))

        shouldThrow<WarnException> {
            chatService.getMessages(memberId = 3L, roomId = room.id, cursor = null, size = 20)
        }
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

    "메시지별 unreadCount 는 발신자를 뺀 안 읽은 참여자 수다. 읽음 커서와 이탈자를 반영한다" {
        // given: 그룹 방 1(나)·2·3·4. 1이 두 개 보냄, 2는 첫 메시지까지 읽음, 4는 이탈
        val room = chatRoomRepository.save(ChatRoomFixture.group(sourceId = 300L, now = FRIDAY)).also { room ->
            chatRoomMemberRepository.saveAll(listOf(1L, 2L, 3L, 4L).map { ChatRoomMember.of(roomId = room.id, memberId = it) })
        }
        val first = chatMessageRepository.save(ChatMessage.of(room.id, 1L, "첫 메시지"))
        val second = chatMessageRepository.save(ChatMessage.of(room.id, 1L, "둘째 메시지"))
        chatService.markAsRead(memberId = 2L, roomId = room.id, lastReadMessageId = first.id)
        chatRoomMemberRepository.findByRoomIdAndMemberId(room.id, 4L)
            ?.apply { leave(FRIDAY) }
            ?.let { chatRoomMemberRepository.save(it) }

        // when
        val page = chatService.getMessages(memberId = 1L, roomId = room.id, cursor = null, size = 30)

        // then: 첫 메시지는 3만 안 읽음(2는 읽음·4는 이탈), 둘째는 2·3이 안 읽음
        page.messages.associate { it.id to it.unreadCount } shouldBe mapOf(first.id to 1, second.id to 2)
    }

    "방 목록의 lastMessage 에도 unreadCount 가 실린다" {
        // given: 1:1 방, 내(1) 메시지를 상대(2)가 아직 안 읽음
        val room = saveOpenedRoom(100L, 1L, 2L)
        chatMessageRepository.save(ChatMessage.of(room.id, 1L, "안녕"))

        // when
        val response = chatService.getMyRooms(memberId = 1L).single()

        // then
        response.lastMessage?.unreadCount shouldBe 1
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

    "읽음 처리는 커서가 실제로 전진했을 때만 직전 커서를 담은 READ 이벤트를 돌려준다" {
        // given
        val room = saveOpenedRoom(100L, 1L, 2L)
        val messages = (1..3).map { chatMessageRepository.save(ChatMessage.of(room.id, 2L, "메시지 $it")) }

        // when: 처음 읽음, 더 앞으로, 같은 값 재시도, 뒤로
        val first = chatService.markAsRead(memberId = 1L, roomId = room.id, lastReadMessageId = messages[1].id)
        val advanced = chatService.markAsRead(memberId = 1L, roomId = room.id, lastReadMessageId = messages[2].id)
        val retried = chatService.markAsRead(memberId = 1L, roomId = room.id, lastReadMessageId = messages[2].id)
        val backward = chatService.markAsRead(memberId = 1L, roomId = room.id, lastReadMessageId = messages[0].id)

        // then
        first shouldBe ChatReadEvent(
            roomId = room.id, memberId = 1L, previousLastReadMessageId = null, lastReadMessageId = messages[1].id,
        )
        advanced shouldBe ChatReadEvent(
            roomId = room.id, memberId = 1L, previousLastReadMessageId = messages[1].id, lastReadMessageId = messages[2].id,
        )
        retried shouldBe null
        backward shouldBe null
    }

    "이 방의 메시지가 아닌 id 로 읽음 처리하면 BAD_REQUEST 이고 커서는 그대로다" {
        // given: 내 방과 다른 방, 다른 방에만 메시지가 있다
        val myRoom = saveOpenedRoom(100L, 1L, 2L)
        val otherRoom = saveOpenedRoom(200L, 1L, 3L)
        val otherRoomMessage = chatMessageRepository.save(ChatMessage.of(otherRoom.id, 3L, "다른 방"))

        // when & then: 다른 방 메시지 id, 존재하지 않는 id 모두 거부
        shouldThrow<WarnException> {
            chatService.markAsRead(memberId = 1L, roomId = myRoom.id, lastReadMessageId = otherRoomMessage.id)
        }.errorCode shouldBe ErrorCode.BAD_REQUEST
        shouldThrow<WarnException> {
            chatService.markAsRead(memberId = 1L, roomId = myRoom.id, lastReadMessageId = Long.MAX_VALUE)
        }.errorCode shouldBe ErrorCode.BAD_REQUEST
        chatRoomMemberRepository.findByRoomIdAndMemberId(myRoom.id, 1L)?.lastReadMessageId shouldBe null
    }

    "이탈자의 읽음은 커서만 전진하고 READ 이벤트는 내지 않는다" {
        // given: 3명 방에서 3L 이 이탈한 뒤 지난 대화를 읽는다
        val room = saveOpenedRoom(100L, 1L, 2L, 3L)
        val message = chatMessageRepository.save(ChatMessage.of(room.id, 1L, "안녕"))
        chatRoomMemberRepository.findByRoomIdAndMemberId(room.id, 3L)
            ?.apply { leave(FRIDAY) }
            ?.let { chatRoomMemberRepository.save(it) }

        // when
        val event = chatService.markAsRead(memberId = 3L, roomId = room.id, lastReadMessageId = message.id)

        // then
        event shouldBe null
        chatRoomMemberRepository.findByRoomIdAndMemberId(room.id, 3L)?.lastReadMessageId shouldBe message.id
    }

    // 잠금 없이 읽으면 두 요청이 둘 다 옛 커서를 보고 늦게 커밋된 쪽이 덮어 커서가 뒤로 갈 수 있다.
    // H2 라 잠금 계약 위반은 잡지만 InnoDB 시맨틱까지 보장하지는 않는다.
    "같은 회원의 읽음 요청이 겹쳐도 커서는 더 큰 값으로 수렴한다" {
        // given
        val room = saveOpenedRoom(100L, 1L, 2L)
        val messages = (1..2).map { chatMessageRepository.save(ChatMessage.of(room.id, 2L, "메시지 $it")) }
        val startLatch = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)

        // when: 1번째·2번째까지 읽음이 동시에 들어온다
        val requests = messages.map { message ->
            executor.submit<ChatReadEvent?> {
                startLatch.await()
                chatService.markAsRead(memberId = 1L, roomId = room.id, lastReadMessageId = message.id)
            }
        }
        startLatch.countDown()
        val events = requests.map { it.get() }
        executor.shutdown()

        // then: 커서는 뒤로 가지 않고, 이벤트의 previous 도 겹치지 않는다
        chatRoomMemberRepository.findByRoomIdAndMemberId(room.id, 1L)?.lastReadMessageId shouldBe messages[1].id
        events.filterNotNull().map { it.previousLastReadMessageId }.toSet().size shouldBe events.filterNotNull().size
    }

    "방 참여자가 아니면 읽음 처리 시 NOT_CHAT_ROOM_MEMBER 예외가 발생한다" {
        // given
        val room = saveOpenedRoom(100L, 1L, 2L)

        // when & then
        shouldThrow<WarnException> {
            chatService.markAsRead(memberId = 99L, roomId = room.id, lastReadMessageId = 1L)
        }.errorCode shouldBe ErrorCode.NOT_CHAT_ROOM_MEMBER
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
        sent.unreadCount shouldBe 1 // 상대(2)가 아직 안 읽음
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
