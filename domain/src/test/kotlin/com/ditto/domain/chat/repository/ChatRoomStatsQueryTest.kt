package com.ditto.domain.chat.repository

import com.ditto.domain.chat.ChatMessageFixture
import com.ditto.domain.chat.ChatRoomFixture
import com.ditto.domain.chat.ChatRoomMemberFixture
import com.ditto.domain.chat.entity.ChatMessageType
import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.repository.querydsl.ChatRoomMemberCount
import com.ditto.domain.chat.repository.querydsl.ChatRoomSenderCount
import com.ditto.domain.match.GroupMatchFixture
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.MemberRole
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QuizSet
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.rematch.RematchFixture
import com.ditto.domain.rematch.repository.RematchRepository
import com.ditto.domain.support.IntegrationTest
import com.ditto.domain.system.OperationWeek
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import java.time.LocalDate
import javax.sql.DataSource

private val WEEK = LocalDate.of(2026, 3, 9)

class ChatRoomStatsQueryTest(
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val personalMatchRepository: PersonalMatchRepository,
    private val groupMatchRepository: GroupMatchRepository,
    private val rematchRepository: RematchRepository,
    private val quizSetRepository: QuizSetRepository,
    private val memberRepository: MemberRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    var sourceIndex = 0L

    fun nextSourceIndex(): Long {
        sourceIndex += 1
        return sourceIndex
    }

    fun saveQuizSet(
        weekStartedOn: LocalDate = WEEK,
        matchingType: MatchingType = MatchingType.ONE_TO_ONE,
        isActive: Boolean = true,
    ): QuizSet = quizSetRepository.save(
        QuizSetFixture.create(
            startDate = weekStartedOn.atStartOfDay(),
            endDate = weekStartedOn.plusDays(2).atTime(23, 59, 59),
            matchingType = matchingType,
            isActive = isActive,
        ),
    )

    fun saveRoom(room: ChatRoom, memberIds: List<Long> = listOf(1L, 2L)): Long {
        val roomId = chatRoomRepository.save(room).id
        memberIds.forEach { memberId ->
            chatRoomMemberRepository.save(ChatRoomMemberFixture.create(roomId = roomId, memberId = memberId))
        }
        return roomId
    }

    fun savePersonalRoom(
        quizSet: QuizSet,
        memberIds: List<Long> = listOf(1L, 2L),
        room: (Long) -> ChatRoom = { ChatRoomFixture.personal(it) },
    ): Long {
        val match = personalMatchRepository.save(
            PersonalMatchFixture.create(
                requesterId = nextSourceIndex(),
                receiverId = nextSourceIndex(),
                quizSetId = quizSet.id,
            ),
        )
        return saveRoom(room(match.id), memberIds)
    }

    fun saveGroupRoom(quizSet: QuizSet, memberIds: List<Long>): Long {
        val groupMatch = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = quizSet.id))
        return saveRoom(ChatRoomFixture.group(groupMatch.id), memberIds)
    }

    // 방은 늘 3/13(금)에 열리고 회차는 원래 그룹의 퀴즈셋으로 센다.
    fun saveRematchRoom(groupQuizSet: QuizSet): Long {
        val index = nextSourceIndex()
        val rematch = rematchRepository.save(
            RematchFixture.create(
                sourceGroupMatchId = index,
                sourceChatRoomId = index,
                quizSetId = groupQuizSet.id,
                week = OperationWeek(groupQuizSet.weekStartedOn),
            ),
        )
        return saveRoom(ChatRoomFixture.rematch(rematch.id))
    }

    fun saveMessage(roomId: Long, senderId: Long, messageType: ChatMessageType = ChatMessageType.TEXT) {
        chatMessageRepository.save(
            ChatMessageFixture.create(roomId = roomId, senderId = senderId, messageType = messageType),
        )
    }

    "countMembersPerOpenedRoomInWeek" - {
        "원본 매칭의 퀴즈셋이 그 주 활성 셋인 열린 방만 센다" {
            val oneToOneSet = saveQuizSet()
            val groupSet = saveQuizSet(matchingType = MatchingType.GROUP)
            val personalRoomId = savePersonalRoom(oneToOneSet)
            val groupRoomId = saveGroupRoom(groupSet, listOf(1L, 2L, 3L))
            val rematchRoomId = saveRematchRoom(groupSet)
            saveRematchRoom(saveQuizSet(weekStartedOn = WEEK.minusWeeks(1), matchingType = MatchingType.GROUP))
            savePersonalRoom(saveQuizSet(isActive = false))
            savePersonalRoom(oneToOneSet) { ChatRoomFixture.personal(it, now = WEEK.atTime(10, 0)) }

            val counts = chatRoomRepository.countMembersPerOpenedRoomInWeek(WEEK)

            counts shouldContainExactlyInAnyOrder listOf(
                ChatRoomMemberCount(personalRoomId, ChatRoomType.PERSONAL, 2L),
                ChatRoomMemberCount(groupRoomId, ChatRoomType.GROUP, 3L),
                ChatRoomMemberCount(rematchRoomId, ChatRoomType.REMATCH, 2L),
            )
        }

        "관리자가 든 방도 센다" {
            val admin = memberRepository.save(
                MemberFixture.create(nickname = "admin", email = "admin@ditto.pics", role = MemberRole.ADMIN),
            )
            val roomId = savePersonalRoom(saveQuizSet(), listOf(admin.id, admin.id + 1))

            val counts = chatRoomRepository.countMembersPerOpenedRoomInWeek(WEEK)

            counts shouldContainExactlyInAnyOrder listOf(ChatRoomMemberCount(roomId, ChatRoomType.PERSONAL, 2L))
        }
    }

    "countSendersPerOpenedRoomInWeek" - {
        "대화 메시지를 보낸 사람을 방마다 한 번씩 세고 SYSTEM 은 뺀다" {
            val groupRoomId = saveGroupRoom(saveQuizSet(matchingType = MatchingType.GROUP), listOf(1L, 2L, 3L))
            saveMessage(groupRoomId, 1L)
            saveMessage(groupRoomId, 1L, ChatMessageType.IMAGE)
            saveMessage(groupRoomId, 2L)
            saveMessage(groupRoomId, 3L, ChatMessageType.SYSTEM)
            val silentRoomId = savePersonalRoom(saveQuizSet())
            saveMessage(silentRoomId, 1L, ChatMessageType.SYSTEM)

            val counts = chatRoomRepository.countSendersPerOpenedRoomInWeek(WEEK)

            counts shouldContainExactlyInAnyOrder listOf(ChatRoomSenderCount(groupRoomId, 2L))
        }
    }
})
