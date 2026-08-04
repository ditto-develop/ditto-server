package com.ditto.api.chat.scheduler

import com.ditto.api.chat.service.ChatRoomEndService
import com.ditto.api.rematch.service.RematchChatRoomOpener
import com.ditto.api.review.service.EndedChatReviewOpener
import java.time.LocalDateTime
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 채팅방을 예약하고 열고 마감하는 스케줄러.
 *
 * 성사된 재매칭의 방 예약도 여기서 한다 — 방을 만드는 주체가 하나여야 같은 쌍의 방이 둘 열리는 것을
 * 잠금 없이 막을 수 있다(ADR 0013). 성사 트랜잭션은 방을 만들지 않는다.
 *
 * 전이 로직은 [ChatRoomEndService]에 있고 사용자 종료와 공유한다. 스케줄러는 의도적으로
 * 실제 시각([LocalDateTime.now])을 사용한다 — 어드민 시각 오버라이드는 온디맨드 조회에만 적용된다.
 *
 * 개방을 먼저 하고 마감을 나중에 한다. 순서를 뒤집으면 개방과 동시에 기한이 지난 방이
 * 다음 주기까지 열린 채로 남는다. 둘 다 멱등이라 주기가 겹치거나 건너뛰어도 결과가 같다.
 *
 * 마감한 방의 평가는 곧바로 열고, 그와 별개로 놓친 방을 매 주기 복구한다 — 채팅 종료와 평가 생성을
 * 한 트랜잭션으로 묶지 않기 때문에(계획서 ⑤-1) 종료만 되고 평가가 안 열린 방이 남을 수 있다.
 */
@Component
class ChatRoomLifecycleScheduler(
    private val chatRoomEndService: ChatRoomEndService,
    private val endedChatReviewOpener: EndedChatReviewOpener,
    private val rematchChatRoomOpener: RematchChatRoomOpener,
) {

    @Scheduled(cron = "\${chat.lifecycle.scheduler.cron:0 * * * * *}")
    fun sweep() {
        val now = LocalDateTime.now()
        // 예약 → 개방 → 마감 순으로 둔다. 방 생명주기 순서라 읽기 쉽고, 순서가 결과를 바꾸지는 않는다 —
        // 개방 시각이 지난 뒤 만들어진 방은 ChatRoom.of 가 곧바로 ACTIVE 로 만들어 openDue 를 기다리지 않는다.
        rematchChatRoomOpener.openMissing()
        chatRoomEndService.openDue(now)

        val ended = chatRoomEndService.endExpired(now)
        endedChatReviewOpener.openFor(ended.map { it.id })
        endedChatReviewOpener.openMissing()
    }
}
