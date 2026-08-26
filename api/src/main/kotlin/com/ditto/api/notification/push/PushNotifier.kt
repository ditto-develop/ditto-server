package com.ditto.api.notification.push

import com.ditto.api.support.runCatchingExceptions
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.member.entity.MemberNotificationSetting
import com.ditto.domain.member.repository.MemberNotificationSettingRepository
import com.ditto.domain.notification.entity.Notification
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.notification.repository.MemberDeviceRepository
import com.ditto.domain.notification.repository.NotificationRepository
import com.ditto.infrastructure.fcm.PushMessage
import com.ditto.infrastructure.fcm.PushSender
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.LocalDateTime
import org.springframework.stereotype.Component

/**
 * 방금 적재된 알림 한 행을 푸시로 내보낸다. 부르는 곳은 `NotificationAppender` 하나다 —
 * 행이 생긴 알림만 오므로 중복 정책이 여기에도 이미 적용돼 있다.
 *
 * **실패를 삼킨다.** 발송을 못 한 것이 적재·비즈니스 흐름을 되돌리면 안 된다.
 * 발송 자체는 비동기(fire-and-forget)라 이 메서드는 FCM 응답을 기다리지 않고,
 * 여기서 잡는 것은 그 앞의 조회(설정·기기·방)다.
 */
@Component
class PushNotifier(
    private val memberNotificationSettingRepository: MemberNotificationSettingRepository,
    private val memberDeviceRepository: MemberDeviceRepository,
    private val notificationRepository: NotificationRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val pushDeadDeviceCleaner: PushDeadDeviceCleaner,
    private val pushSender: PushSender,
) {

    fun push(notification: Notification) {
        runCatchingExceptions { send(notification) }
            .onFailure {
                logger.warn(it) { "푸시 준비 실패 — 무시한다: notificationId=${notification.id}, type=${notification.type}" }
            }
    }

    private fun send(notification: Notification) {
        val memberId = notification.memberId
        if (!allowsPush(notification)) {
            return
        }
        val tokens = memberDeviceRepository.findAllByMemberId(memberId).map { it.token }
        if (tokens.isEmpty()) {
            // 웹 전용 회원 — 앱을 안 쓰면 주소록이 비어 있다. 정상이다.
            return
        }

        pushSender.send(buildMessage(notification, tokens)) { deadTokens ->
            pushDeadDeviceCleaner.clean(deadTokens)
        }
    }

    /** 토글은 회원이 처음 건드릴 때 생성된다 — 행이 없으면 기본값으로 판단한다(조회 API 와 같은 규칙). */
    private fun allowsPush(notification: Notification): Boolean {
        val setting = memberNotificationSettingRepository.findByMemberId(notification.memberId)
            ?: MemberNotificationSetting.defaultOf(notification.memberId)
        return setting.allowsPush(notification.category)
    }

    private fun buildMessage(notification: Notification, tokens: List<String>): PushMessage {
        // FCM 제약: data 값은 전부 문자열이어야 한다.
        val data = buildMap {
            put("notificationId", notification.id.toString())
            put("type", notification.type.name)
            deepLinkOf(notification)?.let { put("deepLink", it) }
        }
        return PushMessage(
            tokens = tokens,
            title = notification.title,
            body = notification.body,
            data = data,
            unreadCount = countUnread(notification.memberId),
        )
    }

    /**
     * 알림을 탭했을 때 앱 웹뷰가 이동할 경로. FE 라우트를 그대로 쓰며 **끝 슬래시가 필수**다
     * (FE 의 `trailingSlash: true` — 없으면 웹뷰 이동에 리다이렉트가 한 번 낀다).
     *
     * 채팅 계열은 방 종류로 경로가 갈린다(`/chat/group/` vs `/chat/one-on-one/`) — FE 방 목록과
     * 같은 이분법이라 재매칭 방도 one-on-one 이다. 방이 그새 지워졌으면 deepLink 없이 보낸다(탭하면 앱만 열림).
     */
    private fun deepLinkOf(notification: Notification): String? {
        val targetId = notification.targetId
        return when (notification.type) {
            NotificationType.MATCH_RESULT -> "/matching/"
            NotificationType.GROUP_FORMED, NotificationType.VOTE_CLOSED -> targetId?.let { "/chat/group/$it/" }
            NotificationType.REMATCH_MATCHED -> targetId?.let { "/chat/one-on-one/$it/" }
            NotificationType.REVIEW_REQUEST -> chatRoomPathOf(targetId)?.let { it + "rate/" }
            NotificationType.CHAT_MESSAGE, NotificationType.CHAT_ENDING_SOON -> chatRoomPathOf(targetId)
            NotificationType.SYSTEM_NOTICE -> null
        }
    }

    private fun chatRoomPathOf(roomId: Long?): String? {
        if (roomId == null) {
            return null
        }
        val room = chatRoomRepository.findById(roomId).orElse(null) ?: return null
        return if (room.sourceType == ChatRoomType.GROUP) "/chat/group/$roomId/" else "/chat/one-on-one/$roomId/"
    }

    /** iOS 앱 아이콘 뱃지 — 벨 배지 API(unread-count)와 같은 기준(30일 창)이라 인앱·아이콘 뱃지가 같은 수다. */
    private fun countUnread(memberId: Long): Int =
        notificationRepository.countByMemberIdAndReadAtIsNullAndCreatedAtGreaterThanEqual(
            memberId,
            LocalDateTime.now().minusDays(Notification.RETENTION_DAYS),
        ).toInt()

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
