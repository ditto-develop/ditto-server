package com.ditto.api.review.service

import com.ditto.api.review.dto.EndedChatRoom
import com.ditto.common.exception.runCatchingExceptions
import com.ditto.domain.chat.entity.ChatRoom
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.review.repository.MemberReviewRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * 끝난 채팅으로 평가를 연다 — 채팅 종료 트랙과 평가 트랙을 잇는 어댑터.
 *
 * 채팅 쪽은 평가를 알지 않는다(`ChatRoomEndService`). 이 어댑터가 [EndedChatRoomLoader]로 종료 결과를
 * 조립해 [MemberReviewService.createReviews]에 넘긴다.
 *
 * **채팅 종료와 평가 생성을 한 트랜잭션으로 묶지 않는다.** 평가 생성 실패가 채팅 종료를 되돌리면
 * 사용자가 나가기를 눌렀는데 실패하기 때문이다. 대신 계약을 at-least-once 로 두고, 놓친 방은
 * [openMissing]이 줍는다. `createReviews`가 멱등이라 중복 생성은 일어나지 않는다.
 *
 * **그룹은 평가보다 재매칭 쌍을 먼저 만든다**(그 순서여야 하는 이유는 [RematchPairCreator]).
 * 중간에 실패해 "쌍만 있고 평가 없음"으로 남는 것은 무해하다 — 쌍은 평가 없이는 쓰이지 않고,
 * 다음 복구 주기가 평가를 열면서 이미 있는 쌍을 건너뛴다.
 */
@Component
class EndedChatReviewOpener(
    private val memberReviewService: MemberReviewService,
    private val rematchPairCreator: RematchPairCreator,
    private val endedChatRoomLoader: EndedChatRoomLoader,
    private val memberReviewRepository: MemberReviewRepository,
    private val chatRoomRepository: ChatRoomRepository,
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
        runCatchingExceptions { openRooms(chatRoomRepository.findAllById(endedRoomIds)) }
            .onFailure { logger.warn(it) { "종료 직후 평가 열기 실패 — 누락 복구에 맡긴다: roomIds=$endedRoomIds" } }
    }

    /**
     * 끝났는데 평가가 없는 방을 찾아 복구한다. 스케줄러가 주기적으로 부른다.
     *
     * [openFor]가 실패했거나 그 사이 앱이 죽어 평가가 안 열린 방을 줍는다. 종료 시각 하한은 두지 않는다 —
     * 얼마나 오래 밀렸든 복구되어야 하기 때문이다(근거는 리포지토리 메서드 KDoc).
     *
     * [openFor]와 달리 자신의 조회 실패는 흡수하지 않는다. 부르는 쪽이 크론이라 예외가 올라가도
     * 그 주기만 건너뛰고 다음 주기에 다시 시도되는 반면, [openFor]는 사용자 종료 요청 경로에 있어
     * 예외가 종료 실패로 보이기 때문이다.
     *
     * @return 평가가 실제로 열린 방 수
     */
    fun openMissing(): Int {
        val roomIds = memberReviewRepository.findEndedChatRoomIdsWithoutReview(RECOVERY_BATCH_SIZE)
        if (roomIds.isEmpty()) {
            return 0
        }

        val rooms = chatRoomRepository.findAllById(roomIds)
        val opened = openRooms(rooms)
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
     * @return 평가가 실제로 열린 방 수
     */
    private fun openRooms(rooms: List<ChatRoom>): Int =
        endedChatRoomLoader.load(rooms)
            .count { endedChatRoom ->
                runCatchingExceptions { openRoom(endedChatRoom) }
                    .onFailure { logger.warn(it) { "평가 열기 실패 — 다음 복구 주기로 넘긴다: roomId=${endedChatRoom.chatRoomId}" } }
                    .isSuccess
            }

    /** 그룹이면 재매칭 쌍을 먼저 만든 뒤 평가를 연다. 1:1 은 쌍 생성이 no-op 이다. */
    private fun openRoom(endedChatRoom: EndedChatRoom) {
        rematchPairCreator.createPairsFor(endedChatRoom)
        memberReviewService.createReviews(endedChatRoom)
    }

    companion object {
        private val logger = KotlinLogging.logger {}

        /** 한 번의 복구가 떠안을 최대 방 수. 장애 후 밀린 물량이 한 호출을 오래 잡지 않게 끊는다. */
        private const val RECOVERY_BATCH_SIZE = 100
    }
}
