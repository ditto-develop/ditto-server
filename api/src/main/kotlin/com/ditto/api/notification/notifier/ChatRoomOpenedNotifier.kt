package com.ditto.api.notification.notifier

import com.ditto.api.notification.message.NotificationMessages
import com.ditto.api.notification.service.NotificationAppender
import com.ditto.api.support.runCatchingExceptions
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * 채팅방이 열리면 참여자에게 알린다 — "이제 대화를 시작할 수 있어요"(기획 알림 문구 표).
 *
 * 개방 트랜잭션이 커밋된 뒤 `ChatRoomLifecycleScheduler`가 이번 주기에 실제로 열린 방만 넘긴다.
 * 매칭이 없어 방이 없는 회원은 자연히 대상이 아니다(표의 예외 "매칭 0건 시 미발송").
 *
 * 방마다 한 번만 알린다(`CHAT_ROOM_OPENED`의 `target_id` = 방 ID). 개방은 `SCHEDULED → ACTIVE` 한 번이라
 * 겹칠 일이 없지만, 중복 정책을 유형에 두면 적재 지점이 늘어도 판단이 갈리지 않는다.
 *
 * **복구 경로가 없다.** 열린 방은 다음 주기에 다시 넘어오지 않으므로, 조회 실패나 개방 커밋 직후 재시작이면
 * 그 방들의 알림은 없다(알림칸에도 남지 않는다). `REVIEW_REQUEST`와 같은 선택이다 — 알림이 없어도 앱을
 * 열면 방이 보이고, 복구하려면 `ChatEndingSoonNotifier`처럼 최근 개방 방을 매 주기 다시 집어오는 수렴 루프로 바꾼다.
 */
@Component
class ChatRoomOpenedNotifier(
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val notificationAppender: NotificationAppender,
) {
    /**
     * 열린 방들의 참여자에게 알린다.
     *
     * **실패를 삼킨다.** 적재 자체는 [NotificationAppender]가 흡수하지만 그 앞의 참여자 조회는 흡수 범위
     * 밖이다. 스케줄러가 부르므로 같은 주기의 다른 처리까지 끌고 가지 않도록 여기서 멈춘다.
     *
     * @return 실제로 남긴 알림 수. 실패했으면 0
     */
    fun notifyOpened(openedRoomIds: Collection<Long>): Int =
        runCatchingExceptions { appendToMembers(openedRoomIds) }
            .onFailure { logger.warn(it) { "채팅방 오픈 알림 실패 — 무시한다: roomIds=$openedRoomIds" } }
            .getOrDefault(0)

    private fun appendToMembers(roomIds: Collection<Long>): Int {
        if (roomIds.isEmpty()) {
            return 0
        }

        val content = NotificationMessages.chatRoomOpened()
        // 열리기 전에 나간 멤버에게는 알리지 않는다 — 이탈자는 더 이상 이 방의 수신자가 아니다.
        val appended = chatRoomMemberRepository.findByRoomIdIn(roomIds)
            .filter { !it.hasLeft }
            .count { notificationAppender.append(it.memberId, content, targetId = it.roomId) }

        if (appended > 0) {
            logger.info { "채팅방 오픈 알림: ${appended}건 (방 ${roomIds.size}개)" }
        }
        return appended
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
