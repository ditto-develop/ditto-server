package com.ditto.api.chat

import com.ditto.api.chat.service.ChatService
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.chat.entity.ChatMessage
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.repository.ChatMessageRepository
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import javax.sql.DataSource

class ChatServiceTest(
    private val chatService: ChatService,
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val chatMessageRepository: ChatMessageRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "1:1 방을 생성하면 두 회원의 멤버 레코드가 함께 생성된다" {
        // when
        chatService.createPersonalRoom(personalMatchId = 100L, memberAId = 1L, memberBId = 2L)

        // then
        val room = chatRoomRepository.findByRoomTypeAndSourceId(ChatRoomType.PERSONAL, 100L)
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

    "내 채팅방 목록은 상대 회원·마지막 메시지·안읽음 수를 담아 반환한다" {
        // given: 방 + 나(1)/상대(2), 메시지 3개, 첫 메시지까지 읽음
        chatService.createPersonalRoom(personalMatchId = 100L, memberAId = 1L, memberBId = 2L)
        val room = chatRoomRepository.findByRoomTypeAndSourceId(ChatRoomType.PERSONAL, 100L)!!
        val first = chatMessageRepository.save(ChatMessage.of(room.id, 2L, "첫 메시지"))
        chatMessageRepository.save(ChatMessage.of(room.id, 2L, "둘째 메시지"))
        val last = chatMessageRepository.save(ChatMessage.of(room.id, 1L, "셋째 메시지"))
        chatService.markAsRead(memberId = 1L, roomId = room.id, lastReadMessageId = first.id)

        // when
        val rooms = chatService.getMyRooms(memberId = 1L)

        // then
        rooms.size shouldBe 1
        val response = rooms[0]
        response.counterpartMemberId shouldBe 2L
        response.lastMessage?.id shouldBe last.id
        response.unreadCount shouldBe 2L
    }

    "메시지 조회는 최신순으로 size 만큼 반환하고 다음 커서를 준다" {
        // given
        chatService.createPersonalRoom(personalMatchId = 100L, memberAId = 1L, memberBId = 2L)
        val room = chatRoomRepository.findByRoomTypeAndSourceId(ChatRoomType.PERSONAL, 100L)!!
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
        chatService.createPersonalRoom(personalMatchId = 100L, memberAId = 1L, memberBId = 2L)
        val room = chatRoomRepository.findByRoomTypeAndSourceId(ChatRoomType.PERSONAL, 100L)!!

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
        chatService.createPersonalRoom(personalMatchId = 100L, memberAId = 1L, memberBId = 2L)
        val room = chatRoomRepository.findByRoomTypeAndSourceId(ChatRoomType.PERSONAL, 100L)!!
        val messages = (1..3).map { chatMessageRepository.save(ChatMessage.of(room.id, 2L, "메시지 $it")) }

        // when: 3번째까지 읽고, 다시 1번째로 되돌리려 시도
        chatService.markAsRead(memberId = 1L, roomId = room.id, lastReadMessageId = messages[2].id)
        chatService.markAsRead(memberId = 1L, roomId = room.id, lastReadMessageId = messages[0].id)

        // then: 앞으로만 전진, 3번째 유지
        val membership = chatRoomMemberRepository.findByRoomIdAndMemberId(room.id, 1L)!!
        membership.lastReadMessageId shouldBe messages[2].id
    }
})
