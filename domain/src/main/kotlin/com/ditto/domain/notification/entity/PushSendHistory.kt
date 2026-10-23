package com.ditto.domain.notification.entity

import com.ditto.domain.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.Comment
import java.time.LocalDateTime

/**
 * 알림 한 건을 푸시로 내보낸 이력. 운영 지표의 알림 종류별 발송·실패 수가 이 행을 센다.
 *
 * 알림 행은 30일 뒤 지워지지만 이 행은 180일 동안 남아서 알림 id 에 외래키를 걸지 않는다.
 * 본문과 토큰은 저장하지 않는다. FCM 이 받았다는 것까지만 알 수 있고 기기에 떴는지는 모른다.
 */
@Entity
@Table(
    name = "push_send_history",
    indexes = [
        // 기간 집계와 보관 기간 정리가 읽는 경로.
        Index(name = "push_send_history_index_1", columnList = "created_at"),
    ],
)
class PushSendHistory private constructor(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("알림 ID")
    @Column(name = "notification_id", nullable = false)
    val notificationId: Long,

    @Comment("수신 회원 ID")
    @Column(name = "member_id", nullable = false)
    val memberId: Long,

    @Comment("알림 유형")
    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 30)
    val notificationType: NotificationType,

    @Comment("보내지 않고 건너뛴 이유 (보냈으면 NULL)")
    @Enumerated(EnumType.STRING)
    @Column(name = "skip_reason", length = 20)
    val skipReason: PushSkipReason?,

    @Comment("보낸 기기 수 (건너뛰었으면 0)")
    @Column(name = "device_count", nullable = false)
    val deviceCount: Int,

    @Comment("실패한 기기 수")
    @Column(name = "failed_device_count", nullable = false)
    val failedDeviceCount: Int,

    @Comment("FCM 실패 코드 (겹치지 않게 쉼표로 이음, 실패가 없으면 NULL)")
    @Column(name = "failure_codes", length = FAILURE_CODES_MAX_LENGTH)
    val failureCodes: String?,
) : BaseEntity() {

    companion object {
        const val RETENTION_DAYS = 180L
        const val FAILURE_CODES_MAX_LENGTH = 255

        fun retentionFrom(): LocalDateTime = LocalDateTime.now().minusDays(RETENTION_DAYS)

        fun skipped(notification: Notification, skipReason: PushSkipReason): PushSendHistory = PushSendHistory(
            notificationId = notification.id,
            memberId = notification.memberId,
            notificationType = notification.type,
            skipReason = skipReason,
            deviceCount = 0,
            failedDeviceCount = 0,
            failureCodes = null,
        )

        /** 기기마다의 실패 코드를 받는다. 성공한 기기는 목록에 없다. */
        fun sent(notification: Notification, deviceCount: Int, failureCodes: List<String>): PushSendHistory =
            PushSendHistory(
                notificationId = notification.id,
                memberId = notification.memberId,
                notificationType = notification.type,
                skipReason = null,
                deviceCount = deviceCount,
                failedDeviceCount = failureCodes.size,
                failureCodes = failureCodes.distinct()
                    .joinToString(",")
                    .take(FAILURE_CODES_MAX_LENGTH)
                    .ifEmpty { null },
            )
    }
}
