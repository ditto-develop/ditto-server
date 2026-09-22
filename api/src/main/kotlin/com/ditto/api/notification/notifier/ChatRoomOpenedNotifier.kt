package com.ditto.api.notification.notifier

import com.ditto.api.notification.message.NotificationMessages
import com.ditto.api.notification.service.NotificationAppender
import com.ditto.api.support.runCatchingExceptions
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component

/**
 * 채팅방이 열리면 참여자에게 알린다.
 *
 * ChatRoomLifecycleScheduler 가 개방 커밋 뒤 이번 주기에 열린 방만 넘긴다. 방이 없는 회원은
 * 대상이 아니므로 "매칭 0건 시 미발송"은 따로 처리하지 않는다.
 *
 * 열린 방은 다음 주기에 다시 넘어오지 않는다. 조회 실패나 커밋 직후 재시작이면 그 방들은 알림이 없다.
 * REVIEW_REQUEST 와 같은 선택이다. 복구가 필요해지면 ChatEndingSoonNotifier 처럼 최근 개방 방을
 * 매 주기 다시 집어오는 방식으로 바꾼다.
 */
@Component
class ChatRoomOpenedNotifier(
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val notificationAppender: NotificationAppender,
) {
    /** 조회 실패는 여기서 삼킨다. 스케줄러의 다른 처리를 막지 않기 위해서다. 실패하면 0. */
    fun notifyOpened(openedRoomIds: Collection<Long>): Int =
        runCatchingExceptions { appendToMembers(openedRoomIds) }
            .onFailure { logger.warn(it) { "채팅방 오픈 알림 실패 — 무시한다: roomIds=$openedRoomIds" } }
            .getOrDefault(0)

    private fun appendToMembers(roomIds: Collection<Long>): Int {
        if (roomIds.isEmpty()) {
            return 0
        }

        val content = NotificationMessages.chatRoomOpened()
        // 열리기 전에 나간 멤버는 제외. 방 단위로 묶어 넘겨 푸시 준비(방 조회)를 방마다 한 번만 한다.
        val appended = chatRoomMemberRepository.findByRoomIdIn(roomIds)
            .filter { !it.hasLeft }
            .groupBy { it.roomId }
            .entries
            .sumOf { (roomId, members) ->
                notificationAppender.appendAll(members.map { it.memberId }, content, targetId = roomId)
            }

        if (appended > 0) {
            logger.info { "채팅방 오픈 알림: ${appended}건 (방 ${roomIds.size}개)" }
        }
        return appended
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
