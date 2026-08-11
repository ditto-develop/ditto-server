package com.ditto.api.notification.notifier

import com.ditto.api.notification.message.NotificationMessages
import com.ditto.api.notification.service.NotificationAppender
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.LocalDateTime
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * 종료가 가까운 채팅방의 참여자에게 알린다 — "채팅이 6시간 후 종료돼요"(피그마 7.2).
 *
 * 상태를 보고 맞추는 수렴 루프다. 종료 예정 시각이 남은 방을 매 주기 다시 집어오지만, 방마다 한 번만
 * 알린다(`CHAT_ENDING_SOON`의 `target_id` = 방 ID) — 알림 행 자체가 처리 완료 표시라 별도 플래그가 없다.
 * 그래서 스케줄러가 멈췄다 살아나도, 남은 시간이 [leadHours]보다 적어진 방은 그때 한 번 알림을 받는다.
 *
 * 채팅 연장(#121)으로 종료 시각이 뒤로 밀리면 다시 알리지 않는다 — 이미 알린 방이기 때문이다.
 * 연장이 붙을 때 다시 판단한다.
 */
@Component
class ChatEndingSoonNotifier(
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val notificationAppender: NotificationAppender,
    @Value("\${chat.ending-soon.lead-hours:6}") private val leadHours: Long,
) {
    /**
     * [now] 기준으로 [leadHours] 안에 끝나는 방의 참여자에게 알린다.
     *
     * @return 실제로 남긴 알림 수
     */
    fun notifyEndingSoon(now: LocalDateTime): Int {
        val roomIds = chatRoomRepository.findAllIdsEndingBetween(now, now.plusHours(leadHours))
        if (roomIds.isEmpty()) {
            return 0
        }

        val content = NotificationMessages.chatEndingSoon(leadHours)
        val appended = chatRoomMemberRepository.findByRoomIdIn(roomIds)
            .count { notificationAppender.append(it.memberId, content, targetId = it.roomId) }

        if (appended > 0) {
            logger.info { "채팅 종료 임박 알림: ${appended}건 (방 ${roomIds.size}개, ${leadHours}시간 전)" }
        }
        return appended
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
