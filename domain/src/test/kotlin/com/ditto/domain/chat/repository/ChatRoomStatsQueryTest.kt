package com.ditto.domain.chat.repository

import com.ditto.domain.chat.ChatMessageFixture
import com.ditto.domain.chat.ChatRoomFixture
import com.ditto.domain.chat.ChatRoomMemberFixture
import com.ditto.domain.chat.entity.ChatMessageType
import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.repository.querydsl.ChatRoomMemberCount
import com.ditto.domain.chat.repository.querydsl.ChatRoomSenderCount
import com.ditto.domain.rematch.RematchFixture
import com.ditto.domain.rematch.repository.RematchRepository
import com.ditto.domain.support.IntegrationTest
import com.ditto.domain.system.OperationWeek
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import java.time.LocalDate
import javax.sql.DataSource

// ChatRoomFixture 의 기본 시각(3/13 금 정오)이 이 주의 주말이다.
private val WEEK = LocalDate.of(2026, 3, 9)

class ChatRoomStatsQueryTest(
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val rematchRepository: RematchRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    var sourceId = 0L

    fun saveRoom(room: ChatRoom, memberIds: List<Long>): Long {
        val roomId = chatRoomRepository.save(room).id
        memberIds.forEach { chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = roomId, memberId = it)) }
        return roomId
    }

    fun saveMessage(roomId: Long, senderId: Long, messageType: ChatMessageType = ChatMessageType.TEXT) {
        chatMessageRepository.save(ChatMessageFixture.create(roomId = roomId, senderId = senderId, messageType = messageType))
    }

    // 원래 그룹은 rematchWeek 이고 방은 그 뒤 금요일(3/13)에 열렸다.
    fun saveRematchRoom(rematchWeek: LocalDate, memberIds: List<Long>): Long {
        sourceId += 1
        val rematch = rematchRepository.save(
            RematchFixture.create(sourceGroupMatchId = sourceId, sourceChatRoomId = sourceId, week = OperationWeek(rematchWeek)),
        )
        return saveRoom(ChatRoomFixture.rematch(sourceId = rematch.id), memberIds)
    }

    fun nextSourceId(): Long {
        sourceId += 1
        return sourceId
    }

    "countMembersPerOpenedRoomOfWeek" - {
        "그 주에 열린 1:1·그룹 방과 그 주 그룹에서 이어진 재매칭 방만 센다" {
            val personalRoomId = saveRoom(ChatRoomFixture.personal(nextSourceId()), listOf(1L, 2L))
            val groupRoomId = saveRoom(ChatRoomFixture.group(nextSourceId()), listOf(1L, 2L, 3L))
            val rematchRoomId = saveRematchRoom(WEEK, listOf(1L, 2L))
            saveRematchRoom(WEEK.minusWeeks(1), listOf(1L, 2L))
            saveRoom(ChatRoomFixture.personal(nextSourceId(), now = WEEK.atTime(10, 0)), listOf(1L, 2L))
            saveRoom(ChatRoomFixture.personal(nextSourceId(), now = ChatRoomFixture.DEFAULT_NOW.plusWeeks(1)), listOf(1L, 2L))

            val counts = chatRoomRepository.countMembersPerOpenedRoomOfWeek(WEEK)

            counts shouldContainExactlyInAnyOrder listOf(
                ChatRoomMemberCount(personalRoomId, ChatRoomType.PERSONAL, 2L),
                ChatRoomMemberCount(groupRoomId, ChatRoomType.GROUP, 3L),
                ChatRoomMemberCount(rematchRoomId, ChatRoomType.REMATCH, 2L),
            )
        }
    }

    "countSendersPerOpenedRoomOfWeek" - {
        "대화 메시지를 보낸 사람을 방마다 한 번씩 세고 SYSTEM 은 뺀다" {
            val groupRoomId = saveRoom(ChatRoomFixture.group(nextSourceId()), listOf(1L, 2L, 3L))
            saveMessage(groupRoomId, 1L)
            saveMessage(groupRoomId, 1L, ChatMessageType.IMAGE)
            saveMessage(groupRoomId, 2L)
            saveMessage(groupRoomId, 3L, ChatMessageType.SYSTEM)
            val silentRoomId = saveRoom(ChatRoomFixture.personal(nextSourceId()), listOf(1L, 2L))
            saveMessage(silentRoomId, 1L, ChatMessageType.SYSTEM)

            val counts = chatRoomRepository.countSendersPerOpenedRoomOfWeek(WEEK)

            counts shouldContainExactlyInAnyOrder listOf(ChatRoomSenderCount(groupRoomId, 2L))
        }
    }
})
