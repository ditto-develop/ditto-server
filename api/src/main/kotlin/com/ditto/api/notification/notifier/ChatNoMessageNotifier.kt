package com.ditto.api.notification.notifier

import com.ditto.api.notification.message.NotificationMessages
import com.ditto.api.notification.service.NotificationAppender
import com.ditto.api.support.runCatchingExceptions
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.LocalDateTime
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * 열린 뒤 [leadHours]가 지나도록 아무도 말하지 않은 방의 참여자에게 첫 인사를 권한다.
 *
 * ChatEndingSoonNotifier 와 같은 구조다. 매 주기 조건에 맞는 방을 다시 집어오고 방마다 한 번만 알린다.
 * 이미 알린 방은 조회 쿼리가 방 단위로 뺀다. 그래서 한 명이라도 적재됐으면 나머지 멤버의 적재 실패는
 * 재시도하지 않는다. 후보는 열린 시각이 (now - lead - 6시간, now - lead] 인 방으로 좁혀 스캔 범위를
 * 최소로 둔다. 스케줄러가 6시간 넘게 멈추면 그 방은 리마인드를 놓친다.
 */
@Component
class ChatNoMessageNotifier(
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val notificationAppender: NotificationAppender,
    @Value("\${chat.no-message.lead-hours:12}") private val leadHours: Long,
) {
    /** 조회 실패는 여기서 삼킨다. 스케줄러의 다른 처리를 막지 않기 위해서다. 실패하면 0. */
    fun notifyNoMessage(now: LocalDateTime): Int =
        runCatchingExceptions { appendToSilentRooms(now) }
            .onFailure { logger.warn(it) { "첫 메시지 리마인드 실패 — 무시한다: now=$now" } }
            .getOrDefault(0)

    private fun appendToSilentRooms(now: LocalDateTime): Int {
        val openedBefore = now.minusHours(leadHours)
        val roomIds = chatRoomRepository.findAllIdsSilentOpenedBetween(
            from = openedBefore.minusHours(SCAN_WINDOW_HOURS),
            to = openedBefore,
        )
        if (roomIds.isEmpty()) {
            return 0
        }

        val content = NotificationMessages.chatNoMessage()
        val appended = chatRoomMemberRepository.findByRoomIdIn(roomIds)
            .filter { !it.hasLeft }
            .groupBy { it.roomId }
            .entries
            .sumOf { (roomId, members) ->
                notificationAppender.appendAll(members.map { it.memberId }, content, targetId = roomId)
            }

        if (appended > 0) {
            logger.info { "첫 메시지 리마인드: ${appended}건 (방 ${roomIds.size}개, 개방 ${leadHours}시간 후)" }
        }
        return appended
    }

    companion object {
        private const val SCAN_WINDOW_HOURS = 6L
        private val logger = KotlinLogging.logger {}
    }
}
