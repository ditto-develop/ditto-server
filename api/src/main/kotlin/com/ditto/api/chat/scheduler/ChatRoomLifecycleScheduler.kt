package com.ditto.api.chat.scheduler

import com.ditto.api.chat.service.ChatRoomEndService
import java.time.LocalDateTime
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 예약된 채팅방을 열고, 기한이 지난 방을 마감하는 스케줄러.
 *
 * 전이 로직은 [ChatRoomEndService]에 있고 사용자 종료와 공유한다. 스케줄러는 의도적으로
 * 실제 시각([LocalDateTime.now])을 사용한다 — 어드민 시각 오버라이드는 온디맨드 조회에만 적용된다.
 *
 * 개방을 먼저 하고 마감을 나중에 한다. 순서를 뒤집으면 개방과 동시에 기한이 지난 방이
 * 다음 주기까지 열린 채로 남는다. 둘 다 멱등이라 주기가 겹치거나 건너뛰어도 결과가 같다.
 */
@Component
class ChatRoomLifecycleScheduler(
    private val chatRoomEndService: ChatRoomEndService,
) {

    @Scheduled(cron = "\${chat.lifecycle.scheduler.cron:0 * * * * *}")
    fun sweep() {
        val now = LocalDateTime.now()
        chatRoomEndService.openDue(now)
        chatRoomEndService.endExpired(now)
    }
}
