package com.ditto.api.chat.scheduler

import com.ditto.api.chat.service.ChatRoomEndService
import com.ditto.api.notification.notifier.ChatEndingSoonNotifier
import com.ditto.api.notification.notifier.ReviewRequestNotifier
import com.ditto.api.rematch.service.RematchChatRoomOpener
import com.ditto.api.review.service.EndedChatReviewOpener
import com.ditto.api.system.ServerTimeProvider
import java.time.LocalDateTime
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 채팅방을 예약하고 열고 마감하는 스케줄러.
 *
 * 성사된 재매칭의 방 예약도 여기서 한다 — 성사 트랜잭션은 방을 만들지 않는다(방 생성 실패가
 * 평가 제출을 되돌리면 안 된다). 방이 금요일에 열리므로 한 주기 늦어도 문제가 없다.
 *
 * 전이 로직은 [ChatRoomEndService]에 있고 사용자 종료와 공유한다.
 *
 * **시계를 저장과 판단으로 가른다**(#146). 개방·마감·임박 알림은 [ServerTimeProvider]의 서버 시각으로
 * 판단해 어드민 시각 오버라이드를 따르고, 재매칭 예약만 실제 시각을 쓴다 — 예약은 `opens_at`을 저장하는데
 * 가짜 시각이 박히면 오버라이드를 끈 뒤 그 방이 미래에 갇힌다. 판단은 `status`만 바꾸므로 저장값이 남지 않는다.
 *
 * 판단이 서버 시각을 봐야 하는 이유: 접근 검사([com.ditto.api.chat.service.ChatRoomAccessChecker])는
 * 시각이 아니라 `status`만 보고, `status`를 바꾸는 경로가 이 스케줄러뿐이다. 여기가 실제 시각을 쓰면
 * 어드민이 시각을 채팅 요일로 맞춰도 방이 열리지 않는다.
 *
 * **오버라이드를 `expires_at` 이후로 옮기면 그 방들이 마감되고 되돌릴 수 없다**(`ACTIVE → ENDED`는
 * 한 번만 일어난다). 시각 오버라이드로 채팅 종료·평가 개방을 확인하려면 감수해야 하는 대가다.
 *
 * 개방을 먼저 하고 마감을 나중에 한다. 순서를 뒤집으면 개방과 동시에 기한이 지난 방이
 * 다음 주기까지 열린 채로 남는다. 둘 다 멱등이라 주기가 겹치거나 건너뛰어도 결과가 같다.
 *
 * 마감한 방의 평가는 곧바로 열고, 그와 별개로 놓친 방을 매 주기 복구한다 — 채팅 종료와 평가 생성을
 * 한 트랜잭션으로 묶지 않기 때문에(계획서 ⑤-1) 종료만 되고 평가가 안 열린 방이 남을 수 있다.
 *
 * 알림(평가 요청·종료 임박)도 여기서 남긴다. 각 전이가 커밋된 뒤에 부르므로 롤백된 전이의 알림이
 * 남지 않고, 알림 적재 실패는 흡수되므로 생명주기 처리를 막지 않는다.
 */
@Component
class ChatRoomLifecycleScheduler(
    private val chatRoomEndService: ChatRoomEndService,
    private val endedChatReviewOpener: EndedChatReviewOpener,
    private val rematchChatRoomOpener: RematchChatRoomOpener,
    private val reviewRequestNotifier: ReviewRequestNotifier,
    private val chatEndingSoonNotifier: ChatEndingSoonNotifier,
    private val serverTimeProvider: ServerTimeProvider,
) {

    @Scheduled(cron = "\${chat.lifecycle.scheduler.cron:0 * * * * *}")
    fun sweep() {
        val serverNow = serverTimeProvider.now()
        // 예약 → 개방 → 마감 순으로 둔다. 방 생명주기 순서라 읽기 쉽다. 예약된 방이 같은 주기에 열릴 수는
        // 있다 — 오버라이드가 예약된 금요일 이후를 가리키면 openDue 가 곧바로 집어간다.
        rematchChatRoomOpener.openMissing(LocalDateTime.now())
        chatRoomEndService.openDue(serverNow)

        val ended = chatRoomEndService.endExpired(serverNow)
        val endedRoomIds = ended.map { it.id }
        endedChatReviewOpener.openFor(endedRoomIds)
        endedChatReviewOpener.openMissing()
        reviewRequestNotifier.notifyFor(endedRoomIds)

        // 종료 임박 알림은 마감 뒤에 둔다 — 이번 주기에 끝난 방이 "곧 종료" 대상으로 잡히지 않는다.
        chatEndingSoonNotifier.notifyEndingSoon(serverNow)
    }
}
