package com.ditto.domain.chat.repository

import com.ditto.domain.chat.ChatRoomFixture
import com.ditto.domain.chat.entity.ChatRoomMember
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.shouldBe
import javax.sql.DataSource

/**
 * `existsUnendedRoomOfMember` — 탈퇴 가드가 쓰는 판정.
 * 방 상태와 참여 여부를 조인해서 보므로 리포지토리 레벨에서 확인한다.
 */
class ChatRoomUnendedQueryTest(
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "참여한 방이 ACTIVE면 참이다" {
        val room = chatRoomRepository.save(ChatRoomFixture.personal(sourceId = 1L))
        chatRoomMemberRepository.save(ChatRoomMember.of(roomId = room.id, memberId = 10L))

        chatRoomRepository.existsUnendedRoomOfMember(10L) shouldBe true
    }

    "참여한 방이 모두 ENDED면 거짓이다" {
        val room = ChatRoomFixture.personal(sourceId = 2L).apply {
            expire(ChatRoomFixture.DEFAULT_NOW.plusDays(3))
        }
        chatRoomRepository.save(room)
        chatRoomMemberRepository.save(ChatRoomMember.of(roomId = room.id, memberId = 11L))

        chatRoomRepository.existsUnendedRoomOfMember(11L) shouldBe false
    }

    "방에 참여하지 않은 회원은 거짓이다 — 남의 방은 세지 않는다" {
        val room = chatRoomRepository.save(ChatRoomFixture.personal(sourceId = 3L))
        chatRoomMemberRepository.save(ChatRoomMember.of(roomId = room.id, memberId = 12L))

        chatRoomRepository.existsUnendedRoomOfMember(99L) shouldBe false
    }

    "끝난 방과 안 끝난 방이 섞여 있으면 참이다" {
        val ended = ChatRoomFixture.personal(sourceId = 4L).apply {
            expire(ChatRoomFixture.DEFAULT_NOW.plusDays(3))
        }
        chatRoomRepository.save(ended)
        val active = chatRoomRepository.save(ChatRoomFixture.personal(sourceId = 5L))
        chatRoomMemberRepository.save(ChatRoomMember.of(roomId = ended.id, memberId = 13L))
        chatRoomMemberRepository.save(ChatRoomMember.of(roomId = active.id, memberId = 13L))

        chatRoomRepository.existsUnendedRoomOfMember(13L) shouldBe true
    }
})
