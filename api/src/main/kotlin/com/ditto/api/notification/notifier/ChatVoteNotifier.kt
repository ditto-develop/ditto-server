package com.ditto.api.notification.notifier

import com.ditto.api.notification.message.NotificationContent
import com.ditto.api.notification.message.NotificationMessages
import com.ditto.api.notification.service.NotificationAppender
import com.ditto.api.support.runCatchingExceptions
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * 만남 투표의 시작·마감을 방의 활성 멤버에게 알린다 — 방을 보고 있지 않은 사람도 알아야 한다.
 *
 * 행위자 본인(생성자·마감자)은 뺀다 — 자기가 눌렀다. 이탈자도 뺀다 — 투표 집계에서 빠지는 것과 같은
 * 기준이다. 시스템 마감(방 종료 동반)은 부르지 않는다 — 방이 끝났다는 사실은 평가 요청 알림이 이미 말한다.
 *
 * **실패를 삼킨다.** 적재는 [NotificationAppender]가 흡수하지만 그 앞의 조회는 흡수 범위 밖이라
 * 여기서 막는다 — 생성·마감 요청 경로에 있어 예외가 올라가면 이미 커밋된 처리가 실패로 보인다.
 */
@Component
class ChatVoteNotifier(
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val notificationAppender: NotificationAppender,
) {

    /** @return 실제로 남긴 알림 수. 실패했으면 0 */
    fun notifyCreated(roomId: Long, createdBy: Long): Int =
        runCatchingExceptions { appendToActiveMembers(roomId, excluding = createdBy, NotificationMessages.voteCreated()) }
            .onFailure { logger.warn(it) { "투표 시작 알림 실패 — 무시한다: roomId=$roomId" } }
            .getOrDefault(0)

    /** @return 실제로 남긴 알림 수. 실패했으면 0 */
    fun notifyClosed(roomId: Long, closedBy: Long): Int =
        runCatchingExceptions { appendToActiveMembers(roomId, excluding = closedBy, NotificationMessages.voteClosed()) }
            .onFailure { logger.warn(it) { "투표 마감 알림 실패 — 무시한다: roomId=$roomId" } }
            .getOrDefault(0)

    private fun appendToActiveMembers(roomId: Long, excluding: Long, content: NotificationContent): Int {
        val receiverIds = chatRoomMemberRepository.findByRoomIdIn(listOf(roomId))
            .filter { !it.hasLeft }
            .map { it.memberId }
            .filter { it != excluding }
        if (receiverIds.isEmpty()) {
            return 0
        }
        return notificationAppender.appendAll(receiverIds, content, targetId = roomId)
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
