package com.ditto.api.review.service

import com.ditto.api.review.dto.EndedChatRoom
import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.review.entity.MemberReview
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.LocalDate
import org.springframework.stereotype.Component

/**
 * 끝난 방으로 평가 입력 계약([EndedChatRoom])을 만든다.
 *
 * `chat_room`에 없는 값을 원본 매칭에서 읽어 채우므로 순수 변환이 아니다 — `quizSetId`는 원본 매칭에,
 * `weekStartedOn`은 그 퀴즈셋에 있다. 원본이 유형별로 다른 테이블이라는 사실도 여기서 흡수해,
 * 평가를 여는 쪽([EndedChatReviewOpener])은 유형을 신경 쓰지 않는다.
 */
@Component
class EndedChatRoomLoader(
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val personalMatchRepository: PersonalMatchRepository,
    private val groupMatchRepository: GroupMatchRepository,
    private val quizSetRepository: QuizSetRepository,
) {
    /**
     * 방 수만큼 조회하지 않도록 필요한 것을 먼저 일괄로 읽은 뒤 방마다 짝지운다.
     *
     * 평가를 열지 않는 유형([MemberReview.REVIEWABLE_MATCH_TYPES])은 조용히 빠진다 — 실패가 아니라
     * 정책이므로 로그를 남기지 않는다. 값이 빠진 방은 [toEndedChatRoom]이 건너뛰며 그때는 WARN 을 남긴다.
     * 그래서 돌려주는 목록이 입력보다 짧을 수 있다.
     */
    fun load(rooms: List<ChatRoom>): List<EndedChatRoom> {
        val reviewableRooms = rooms.filter { it.sourceType in MemberReview.REVIEWABLE_MATCH_TYPES }
        if (reviewableRooms.isEmpty()) {
            return emptyList()
        }

        val quizSetIdByRoomId = findQuizSetIdByRoomId(reviewableRooms)
        val weekStartedOnByQuizSetId = findWeekStartedOnByQuizSetId(quizSetIdByRoomId.values)
        val participantIdsByRoomId = findParticipantIdsByRoomId(reviewableRooms)

        return reviewableRooms.mapNotNull { room ->
            toEndedChatRoom(
                room = room,
                quizSetId = quizSetIdByRoomId[room.id],
                weekStartedOnByQuizSetId = weekStartedOnByQuizSetId,
                participantIds = participantIdsByRoomId[room.id].orEmpty(),
            )
        }
    }

    /**
     * 방마다 원본 매칭을 타고 `quizSetId`를 찾는다. 원본이 없는 방은 map 에 담기지 않는다.
     *
     * [MemberReview.REVIEWABLE_MATCH_TYPES]로 걸러진 뒤라 유형은 `PERSONAL`·`GROUP` 둘뿐이다.
     */
    private fun findQuizSetIdByRoomId(rooms: List<ChatRoom>): Map<Long, Long> {
        val (personalRooms, groupRooms) = rooms.partition { it.sourceType == ChatRoomType.PERSONAL }
        val personalQuizSetIdByMatchId = personalMatchRepository.findAllById(personalRooms.map { it.sourceId })
            .associate { it.id to it.quizSetId }
        val groupQuizSetIdByMatchId = groupMatchRepository.findAllById(groupRooms.map { it.sourceId })
            .associate { it.id to it.quizSetId }

        return (
            personalRooms.mapNotNull { room -> personalQuizSetIdByMatchId[room.sourceId]?.let { room.id to it } } +
                groupRooms.mapNotNull { room -> groupQuizSetIdByMatchId[room.sourceId]?.let { room.id to it } }
            ).toMap()
    }

    private fun findWeekStartedOnByQuizSetId(quizSetIds: Collection<Long>): Map<Long, LocalDate> =
        quizSetRepository.findAllById(quizSetIds).associate { it.id to it.weekStartedOn }

    private fun findParticipantIdsByRoomId(rooms: List<ChatRoom>): Map<Long, List<Long>> =
        chatRoomMemberRepository.findByRoomIdIn(rooms.map { it.id }).groupBy({ it.roomId }, { it.memberId })

    /**
     * 조립에 필요한 값이 하나라도 없으면 그 방은 건너뛴다.
     *
     * 원본이 사라졌거나(탈퇴 hard delete 등) 종료 시각이 없는 방이 그렇다. 여기서 터뜨리면
     * 같은 배치의 정상 방들까지 막히므로, 건너뛰고 로그로만 남겨 다음 복구 주기에 다시 시도한다.
     */
    private fun toEndedChatRoom(
        room: ChatRoom,
        quizSetId: Long?,
        weekStartedOnByQuizSetId: Map<Long, LocalDate>,
        participantIds: List<Long>,
    ): EndedChatRoom? {
        val weekStartedOn = quizSetId?.let { weekStartedOnByQuizSetId[it] }
        val endedAt = room.endedAt

        if (quizSetId == null || weekStartedOn == null || endedAt == null) {
            logger.warn {
                "평가를 열 수 없어 건너뜀: roomId=${room.id}, sourceType=${room.sourceType}, " +
                    "sourceId=${room.sourceId}, quizSetId=$quizSetId, " +
                    "weekStartedOn=${weekStartedOn != null}, endedAt=$endedAt"
            }
            return null
        }

        return EndedChatRoom(
            chatRoomId = room.id,
            matchType = room.sourceType,
            matchId = room.sourceId,
            quizSetId = quizSetId,
            weekStartedOn = weekStartedOn,
            participantIds = participantIds,
            endedAt = endedAt,
        )
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
