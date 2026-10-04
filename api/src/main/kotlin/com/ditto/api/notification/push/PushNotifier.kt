package com.ditto.api.notification.push

import com.ditto.api.notification.deeplink.NotificationDeepLinks
import com.ditto.api.support.runCatchingExceptions
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.member.entity.MemberNotificationSetting
import com.ditto.domain.member.repository.MemberNotificationSettingRepository
import com.ditto.domain.notification.entity.Notification
import com.ditto.domain.notification.entity.NotificationCategory
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.notification.repository.MemberDeviceRepository
import com.ditto.domain.notification.repository.NotificationRepository
import com.ditto.infrastructure.fcm.PushMessage
import com.ditto.infrastructure.fcm.PushSender
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Duration
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 적재된 알림을 푸시로 내보낸다. `NotificationAppender`만 부른다.
 *
 * 발송 실패가 비즈니스 흐름을 되돌리면 안 되므로 준비 조회의 예외는 삼킨다.
 * 발송 자체는 fire-and-forget 이라 FCM 응답을 기다리지 않는다.
 */
@Component
class PushNotifier(
    private val memberNotificationSettingRepository: MemberNotificationSettingRepository,
    private val memberDeviceRepository: MemberDeviceRepository,
    private val notificationRepository: NotificationRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val notificationDeepLinks: NotificationDeepLinks,
    private val pushDeadDeviceCleaner: PushDeadDeviceCleaner,
    private val pushSender: PushSender,
) {

    /**
     * 같은 사건으로 생긴 알림들을 내보낸다. deepLink 는 유형·대상이 같으면 동일하므로
     * 한 번만 계산한다(그룹 채팅 한 건이 수신자 수만큼 방을 조회하지 않게).
     *
     * `NOT_SUPPORTED`: 트랜잭션 안 적재 지점(`GroupMatchService.joinGroupMatch`)에서 호출자
     * 트랜잭션에 합류하면 미읽음 수가 옛 스냅샷으로 계산돼 방금 커밋된 알림이 뱃지에서 빠진다.
     * 조회가 호출자 세션을 건드리며 생길 수 있는 rollback-only 오염도 함께 막는다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun pushAll(notifications: List<Notification>) {
        val first = notifications.firstOrNull() ?: return
        runCatchingExceptions {
            val deepLink = notificationDeepLinks.deepLinkFor(first)
            val mutedMemberIds = mutedMemberIdsOf(first)
            notifications
                .filter { it.memberId !in mutedMemberIds }
                .forEach { send(it, deepLink) }
        }.onFailure {
            logger.warn(it) { "푸시 준비 실패 — 무시한다: type=${first.type}, targetId=${first.targetId}" }
        }
    }

    private fun send(notification: Notification, deepLink: String?) {
        if (!allowsPush(notification)) {
            return
        }
        val tokens = memberDeviceRepository.findAllByMemberId(notification.memberId).map { it.token }
        if (tokens.isEmpty()) {
            return // 웹 전용 회원
        }

        pushSender.send(buildMessage(notification, tokens, deepLink), pushDeadDeviceCleaner::clean)
    }

    /**
     * 이 방 알림을 끈 회원들. 채팅 카테고리 알림(새 메시지·방 오픈·첫 메시지 리마인드·종료 임박·투표 생성/마감)만 막는다 —
     * 모두 `targetId = chat_room.id` 다. 같은 사건의 알림은 방이 같으므로 한 번만 조회한다.
     * 평가 요청처럼 끝난 방을 가리키는 매칭 카테고리 알림은 막지 않는다.
     */
    private fun mutedMemberIdsOf(first: Notification): Set<Long> {
        val roomId = first.targetId
        if (first.category != NotificationCategory.CHAT || roomId == null) {
            return emptySet()
        }
        return chatRoomMemberRepository.findByRoomId(roomId).filter { it.isMuted }.map { it.memberId }.toSet()
    }

    /** 설정 행은 회원이 토글을 처음 건드릴 때 생기므로, 없으면 기본값으로 판단한다. */
    private fun allowsPush(notification: Notification): Boolean {
        val setting = memberNotificationSettingRepository.findByMemberId(notification.memberId)
            ?: MemberNotificationSetting.defaultOf(notification.memberId)
        return setting.allowsPush(notification.category)
    }

    private fun buildMessage(notification: Notification, tokens: List<String>, deepLink: String?): PushMessage {
        val data = buildMap {
            put("notificationId", notification.id.toString())
            put(PushMessage.DATA_KEY_TYPE, notification.type.name)
            deepLink?.let { put("deepLink", it) }
        }
        return PushMessage(
            tokens = tokens,
            title = notification.title,
            body = notification.body,
            data = data,
            unreadCount = countUnread(notification.memberId),
            ttl = ttlOf(notification.type),
        )
    }

    /** 벨 배지 API 와 같은 창을 써야 인앱과 아이콘 뱃지가 같은 수가 된다. */
    private fun countUnread(memberId: Long): Int =
        notificationRepository.countUnread(memberId, Notification.retentionFrom()).toInt()

    /** 시효가 있는 알림만 짧게. 없으면 FCM 기본(4주)이라 꺼져 있던 기기에 지난 알림이 몰린다. */
    private fun ttlOf(type: NotificationType): Duration? = when (type) {
        NotificationType.CHAT_MESSAGE -> Duration.ofHours(1)
        // 방이 열려 있는 72시간(금 00:00 ~ 월 00:00)이 지나면 무의미하다. 퀴즈 응답 기간(월~수)도 같다.
        NotificationType.CHAT_ROOM_OPENED,
        NotificationType.CHAT_NO_MESSAGE,
        NotificationType.QUIZ_OPENED,
        -> Duration.ofDays(3)
        // 종료·마감 6시간 전 알림 — 지나면 무의미하다.
        NotificationType.CHAT_ENDING_SOON, NotificationType.QUIZ_CLOSING_SOON -> Duration.ofHours(6)
        else -> null
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
