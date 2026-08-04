package com.ditto.api.review.service

import com.ditto.api.review.dto.EndedChatRoom
import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.match.entity.PersonalMatch
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.review.repository.MemberReviewRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.LocalDate
import org.springframework.stereotype.Component

/**
 * 끝난 1:1 채팅으로 평가를 연다 — 채팅 종료 트랙과 평가 트랙을 잇는 어댑터.
 *
 * 채팅 쪽은 평가를 알지 않는다(`ChatRoomEndService`). 이 어댑터가 종료 결과를 [EndedChatRoom]으로
 * 조립해 [MemberReviewService.createReviews]에 넘긴다.
 *
 * **채팅 종료와 평가 생성을 한 트랜잭션으로 묶지 않는다.** 평가 생성 실패가 채팅 종료를 되돌리면
 * 사용자가 나가기를 눌렀는데 실패하기 때문이다. 대신 계약을 at-least-once 로 두고, 놓친 방은
 * [openMissing]이 줍는다. `createReviews`가 멱등이라 중복 생성은 일어나지 않는다.
 *
 * 그룹은 범위 밖이다 — 멤버십 동결·재매칭 pair 생성이 얽혀 별도 트랙(`I1G`)이다.
 */
@Component
class EndedChatReviewOpener(
    private val memberReviewService: MemberReviewService,
    private val memberReviewRepository: MemberReviewRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val personalMatchRepository: PersonalMatchRepository,
    private val quizSetRepository: QuizSetRepository,
) {
    /**
     * 방금 끝난 방들의 평가를 곧바로 연다. 종료 응답이 나가기 전에 평가가 열려 있어야
     * 사용자가 종료 직후 평가 화면으로 넘어갈 수 있다.
     *
     * 실패해도 예외를 올리지 않는다 — 이 시점의 채팅 종료는 이미 커밋됐고, 여기서 터뜨리면
     * 종료 요청이 실패한 것처럼 보인다. 놓친 방은 [openMissing]이 복구한다.
     */
    fun openFor(endedRoomIds: Collection<Long>) {
        if (endedRoomIds.isEmpty()) {
            return
        }
        // 방마다 격리되므로 여기서 전체를 감쌀 필요가 없다. 조회 자체가 실패하는 경우만 남는다.
        runCatchingExceptions { openEach(chatRoomRepository.findAllById(endedRoomIds)) }
            .onFailure { logger.warn(it) { "종료 직후 평가 열기 실패 — 누락 복구에 맡긴다: roomIds=$endedRoomIds" } }
    }

    /**
     * 끝났는데 평가가 없는 방을 찾아 복구한다. 스케줄러가 주기적으로 부른다.
     *
     * [openFor]가 실패했거나 그 사이 앱이 죽어 평가가 안 열린 방을 줍는다. 종료 시각 하한은 두지 않는다 —
     * 얼마나 오래 밀렸든 복구되어야 하기 때문이다(근거는 리포지토리 메서드 KDoc).
     */
    fun openMissing(): Int {
        val roomIds = memberReviewRepository.findEndedChatRoomIdsWithoutReview(
            sourceType = ChatRoomType.PERSONAL,
            limit = RECOVERY_BATCH_SIZE,
        )
        if (roomIds.isEmpty()) {
            return 0
        }

        val rooms = chatRoomRepository.findAllById(roomIds)
        val opened = openEach(rooms)
        logger.info { "평가 누락 복구: 대상 ${rooms.size}건 중 ${opened}건 열림" }
        return opened
    }

    /**
     * 방마다 독립적으로 평가를 연다 — **한 방의 실패가 다른 방을 막지 않아야 한다.**
     *
     * 트랜잭션을 여기서 열지 않는 것이 핵심이다. `createReviews`는 다른 빈이라 호출마다 자기
     * 트랜잭션을 얻으므로, 방 하나가 실패해도 그 방만 롤백된다. 배치 전체를 한 트랜잭션으로 묶으면
     * 뒤쪽 한 건 때문에 앞서 성공한 것까지 폐기되고, anti-join 이 그 방을 매 주기 다시 집어오므로
     * **독이 든 방 하나가 복구를 영구히 막는다**(예: 참여자 0명이면 `createReviews`가 예외를 던진다).
     *
     * 그룹은 건너뛴다 — 멤버십 동결·재매칭 pair 가 얽혀 별도 트랙(`I1G`)이다.
     *
     * @return 평가가 실제로 열린 방 수
     */
    private fun openEach(rooms: List<ChatRoom>): Int =
        loadEndedChatRooms(rooms.filter { it.sourceType == ChatRoomType.PERSONAL })
            .count { endedChatRoom ->
                runCatchingExceptions { memberReviewService.createReviews(endedChatRoom) }
                    .onFailure { logger.warn(it) { "평가 열기 실패 — 다음 복구 주기로 넘긴다: roomId=${endedChatRoom.chatRoomId}" } }
                    .isSuccess
            }

    /**
     * 방마다 평가 입력 계약([EndedChatRoom])을 만든다. `chat_room`에 없는 값을 원본 매칭에서 읽어 채우므로
     * 순수 변환이 아니다 — `quizSetId`는 `PersonalMatch`, `weekStartedOn`은 그 퀴즈셋에 있다.
     *
     * 방 수만큼 조회하지 않도록 필요한 것을 먼저 일괄로 읽은 뒤 방마다 짝지운다.
     * 값이 빠진 방은 [toEndedChatRoom]이 건너뛰므로, 돌려주는 목록이 입력보다 짧을 수 있다.
     */
    private fun loadEndedChatRooms(rooms: List<ChatRoom>): List<EndedChatRoom> {
        if (rooms.isEmpty()) {
            return emptyList()
        }

        val matchesById = findMatchesBySourceId(rooms)
        val weekStartedOnByQuizSetId = findWeekStartedOnByQuizSetId(matchesById.values)
        val participantIdsByRoomId = findParticipantIdsByRoomId(rooms)

        return rooms.mapNotNull { room ->
            toEndedChatRoom(
                room = room,
                match = matchesById[room.sourceId],
                weekStartedOnByQuizSetId = weekStartedOnByQuizSetId,
                participantIds = participantIdsByRoomId[room.id].orEmpty(),
            )
        }
    }

    /** 방의 `sourceId`가 곧 `PersonalMatch.id`다. */
    private fun findMatchesBySourceId(rooms: List<ChatRoom>): Map<Long, PersonalMatch> =
        personalMatchRepository.findAllById(rooms.map { it.sourceId }).associateBy { it.id }

    private fun findWeekStartedOnByQuizSetId(matches: Collection<PersonalMatch>): Map<Long, LocalDate> =
        quizSetRepository.findAllById(matches.map { it.quizSetId }).associate { it.id to it.weekStartedOn }

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
        match: PersonalMatch?,
        weekStartedOnByQuizSetId: Map<Long, LocalDate>,
        participantIds: List<Long>,
    ): EndedChatRoom? {
        val weekStartedOn = match?.let { weekStartedOnByQuizSetId[it.quizSetId] }
        val endedAt = room.endedAt

        if (match == null || weekStartedOn == null || endedAt == null) {
            logger.warn {
                "평가를 열 수 없어 건너뜀: roomId=${room.id}, sourceId=${room.sourceId}, " +
                    "match=${match != null}, weekStartedOn=${weekStartedOn != null}, endedAt=$endedAt"
            }
            return null
        }

        return EndedChatRoom(
            chatRoomId = room.id,
            matchType = room.sourceType,
            matchId = room.sourceId,
            quizSetId = match.quizSetId,
            weekStartedOn = weekStartedOn,
            participantIds = participantIds,
            endedAt = endedAt,
        )
    }

    /**
     * [runCatching]과 같지만 [Error]는 삼키지 않는다.
     *
     * 이 어댑터는 한 방의 실패를 흡수하고 다음 방으로 넘어가야 해서 실패를 값으로 받는 편이 읽기 좋다.
     * 다만 `runCatching`은 [Throwable]을 잡으므로 `OutOfMemoryError` 같은 치명 오류까지 WARN 한 줄로
     * 묻히고, 이미 불안정한 JVM 에서 다음 방 처리를 계속 시도하게 된다. 그건 통과시킨다.
     */
    private inline fun runCatchingExceptions(block: () -> Unit): Result<Unit> =
        runCatching(block).onFailure { if (it !is Exception) throw it }

    companion object {
        private val logger = KotlinLogging.logger {}

        /** 한 번의 복구가 떠안을 최대 방 수. 장애 후 밀린 물량이 한 호출을 오래 잡지 않게 끊는다. */
        private const val RECOVERY_BATCH_SIZE = 100

    }
}
