package com.ditto.api.notification.service

import com.ditto.api.notification.dto.NotificationResponse
import com.ditto.api.notification.dto.NotificationsResponse
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.notification.entity.Notification
import com.ditto.domain.notification.entity.NotificationCategory
import com.ditto.domain.notification.repository.NotificationRepository
import java.time.LocalDateTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 알림 센터(피그마 7.2) 조회·읽음. 적재는 [NotificationAppender]가 담당하며 이 서비스는 쓰지 않는다.
 *
 * **실제 시각으로 동작한다** — 어드민 시각 오버라이드(`ServerTimeProvider`)를 쓰지 않는다.
 * 알림의 `created_at`은 JPA Auditing 이 실제 시각으로 채우므로, 조회 창을 가짜 시각으로 계산하면
 * 오버라이드가 미래일 때 방금 온 알림이 창 밖으로 밀려 사라진다.
 */
@Service
@Transactional(readOnly = true)
class NotificationService(
    private val notificationRepository: NotificationRepository,
) {

    /**
     * 알림 목록(최신순 커서 페이징). [category]가 null 이면 전체 칩이다.
     *
     * 보관 기간(30일) 안의 알림만 보여준다 — 그 밖은 purge 대상이라 곧 사라진다.
     */
    fun getNotifications(
        memberId: Long,
        category: NotificationCategory?,
        cursor: Long?,
        size: Int,
    ): NotificationsResponse {
        val pageSize = size.coerceIn(1, MAX_PAGE_SIZE)
        val notifications = notificationRepository.findByMemberIdWithCursor(
            memberId = memberId,
            category = category,
            cursor = cursor,
            size = pageSize,
            from = Notification.retentionFrom(),
        )

        return NotificationsResponse(
            notifications = notifications.map { NotificationResponse.from(it) },
            // 마지막 페이지를 정확히 채운 경우에도 커서를 준다 — 다음 요청이 빈 목록을 받고 끝난다.
            nextCursor = if (notifications.size == pageSize) notifications.last().id else null,
        )
    }

    /** 홈 헤더 벨 배지용 미읽음 수 */
    fun getUnreadCount(memberId: Long): Long =
        notificationRepository.countByMemberIdAndReadAtIsNullAndCreatedAtGreaterThanEqual(memberId, Notification.retentionFrom())

    /**
     * 알림 하나를 읽음으로 표시한다. 이미 읽은 알림에 다시 요청해도 성공한다(멱등).
     *
     * 남의 알림은 [ErrorCode.NOT_FOUND]다 — 존재 여부를 알려주지 않으려고 403 과 구분하지 않는다.
     */
    @Transactional
    fun markRead(memberId: Long, notificationId: Long) {
        val notification = notificationRepository.findByIdAndMemberId(notificationId, memberId)
            ?: throw WarnException(ErrorCode.NOT_FOUND, "존재하지 않는 알림입니다.")
        notification.markRead(LocalDateTime.now())
    }

    /**
     * 안읽은 알림을 모두 읽음으로 표시한다 — 화면 우상단 "모두 읽음".
     *
     * 보관 기간 밖의 알림까지 함께 읽음으로 만든다. 화면에 안 보이는 알림이 안읽음으로 남아 있으면
     * 배지가 0이 되지 않는 것처럼 보일 수 있는데, 배지도 같은 창을 쓰므로 실제로는 문제가 없다.
     * 그래도 조건을 좁히지 않는 편이 단순하고, 어차피 purge 대상이다.
     *
     * @return 이번 호출로 읽음이 된 건수
     */
    @Transactional
    fun markAllRead(memberId: Long): Long = notificationRepository.markAllRead(memberId, LocalDateTime.now())


    companion object {
        /** 한 페이지 최대 건수. 채팅 메시지 페이징과 같은 상한을 쓴다. */
        private const val MAX_PAGE_SIZE = 100
    }
}
