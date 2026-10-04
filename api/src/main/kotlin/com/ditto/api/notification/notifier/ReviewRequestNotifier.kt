package com.ditto.api.notification.notifier

import com.ditto.api.notification.message.NotificationMessages
import com.ditto.api.notification.service.NotificationAppender
import com.ditto.api.review.service.MemberReviewService
import com.ditto.api.support.runCatchingExceptions
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.member.repository.MemberRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * 채팅이 끝나 평가가 열린 방의 참여자에게 알린다 — 채팅 종료 트랙과 알림을 잇는 어댑터.
 *
 * **재매칭 방은 제외한다.** 재매칭 채팅이 끝나면 평가를 열지 않기로 했으므로(#132 결정), 알리면
 * 평가할 것이 없는 화면으로 보낸다. `EndedChatReviewOpener`와 같은 기준을 쓰지만 그 클래스에 얹지
 * 않는다 — 평가 열기는 실패해도 복구되는 at-least-once 경로이고, 알림은 못 남기면 그냥 없다.
 *
 * 방마다 한 번만 알린다(`REVIEW_REQUEST`의 `target_id` = 방 ID). 사용자 종료와 만료 스케줄러가 같은
 * 방을 겹쳐 넘겨도 알림은 하나다.
 */
@Component
class ReviewRequestNotifier(
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val memberRepository: MemberRepository,
    private val notificationAppender: NotificationAppender,
) {
    /**
     * 끝난 방들의 참여자에게 평가 요청 알림을 남긴다.
     *
     * **실패를 삼킨다.** 적재 자체는 [NotificationAppender]가 흡수하지만 그 앞의 조회는 흡수 범위 밖이라
     * 여기서 막는다 — 사용자 종료 경로(`ChatController.end`)에 있어 예외가 올라가면 이미 커밋된 종료가
     * 실패로 보이고, 재시도해도 종료된 방은 아무것도 다시 하지 않는다.
     *
     * @return 실제로 남긴 알림 수. 실패했으면 0
     */
    fun notifyFor(endedRoomIds: Collection<Long>): Int =
        runCatchingExceptions { appendReviewRequests(endedRoomIds) }
            .onFailure { logger.warn(it) { "평가 요청 알림 실패 — 무시한다: roomIds=$endedRoomIds" } }
            .getOrDefault(0)

    /**
     * 열린 그룹 방에서 나간 사람에게만 평가 요청을 남긴다. 방이 끝날 때 다시 보내도 대상당 한 번이라
     * 이 사람에게는 겹쳐 가지 않는다. 실패는 [notifyFor]처럼 삼킨다.
     */
    fun notifyLeaver(roomId: Long, memberId: Long): Boolean =
        runCatchingExceptions { appendReviewRequestToLeaver(roomId, memberId) }
            .onFailure { logger.warn(it) { "나간 사람 평가 요청 알림 실패, 무시한다: roomId=$roomId, memberId=$memberId" } }
            .getOrDefault(false)

    private fun appendReviewRequestToLeaver(roomId: Long, memberId: Long): Boolean {
        val memberIds = chatRoomMemberRepository.findByRoomId(roomId).map { it.memberId }
        if (memberIds.size < MemberReviewService.MIN_REVIEWER_COUNT) {
            return false
        }
        return appendReviewRequest(roomId, memberId, memberIds, nicknamesOf(memberIds))
    }

    private fun appendReviewRequests(endedRoomIds: Collection<Long>): Int {
        if (endedRoomIds.isEmpty()) {
            return 0
        }

        val reviewableRoomIds = chatRoomRepository.findAllById(endedRoomIds)
            .filter { it.sourceType != ChatRoomType.REMATCH }
            .map { it.id }
        if (reviewableRoomIds.isEmpty()) {
            return 0
        }

        // 평가 명단과 같은 기준이라 나간 사람도 받는다. 2명 미만 방은 평가가 안 열리니 보내지 않는다.
        val membersByRoomId = chatRoomMemberRepository.findByRoomIdIn(reviewableRoomIds)
            .groupBy { it.roomId }
            .filterValues { it.size >= MemberReviewService.MIN_REVIEWER_COUNT }
        val nicknamesById = nicknamesOf(membersByRoomId.values.flatten().map { it.memberId })

        val appended = reviewableRoomIds.sumOf { roomId ->
            val memberIds = membersByRoomId[roomId].orEmpty().map { it.memberId }
            memberIds.count { memberId -> appendReviewRequest(roomId, memberId, memberIds, nicknamesById) }
        }

        if (appended > 0) {
            logger.info { "평가 요청 알림: ${appended}건 (방 ${reviewableRoomIds.size}개)" }
        }
        return appended
    }

    private fun appendReviewRequest(
        roomId: Long,
        memberId: Long,
        roomMemberIds: List<Long>,
        nicknamesById: Map<Long, String>,
    ): Boolean {
        val counterpartNicknames = roomMemberIds
            .filter { it != memberId }
            .mapNotNull { nicknamesById[it] }
        return notificationAppender.append(
            memberId = memberId,
            content = NotificationMessages.reviewRequest(counterpartNicknames),
            targetId = roomId,
        )
    }

    private fun nicknamesOf(memberIds: Collection<Long>): Map<Long, String> =
        memberRepository.findAllById(memberIds.distinct()).associate { it.id to it.nickname }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
