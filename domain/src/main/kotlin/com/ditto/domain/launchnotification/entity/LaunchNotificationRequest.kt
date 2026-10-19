package com.ditto.domain.launchnotification.entity

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
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
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime
import org.hibernate.annotations.Comment

/**
 * 앱 출시 알림 신청 1건 — 로그인하지 않은 방문자가 남긴 이메일.
 *
 * 같은 (이메일, 플랫폼)은 한 행이다. 다시 신청하면 새 행 없이 동의 시각·버전만 바뀐다([reconsent]).
 * 출시 안내를 보내면 어드민이 벌크 UPDATE로 발송 시각([notifiedAt])을 찍고, 그로부터 [RETENTION_DAYS]일 뒤 파기한다.
 */
@Entity
@Table(
    name = "launch_notification_request",
    uniqueConstraints = [
        UniqueConstraint(name = "launch_notification_request_uk_1", columnNames = ["email", "platform"]),
    ],
    indexes = [
        Index(name = "launch_notification_request_index_1", columnList = "notified_at"),
    ],
)
class LaunchNotificationRequest private constructor(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("이메일 (trim·소문자 정규화)")
    @Column(nullable = false, length = EMAIL_MAX_LENGTH)
    val email: String,

    @Comment("출시 알림 받을 플랫폼")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val platform: LaunchPlatform,

    consentVersion: String,
    consentedAt: LocalDateTime,
) : BaseEntity() {

    @Comment("동의한 개인정보 수집 문구 버전 (FE가 보여 준 문구)")
    @Column(name = "consent_version", nullable = false, length = CONSENT_VERSION_MAX_LENGTH)
    var consentVersion: String = consentVersion
        protected set

    @Comment("마지막으로 동의한 일시")
    @Column(name = "consented_at", nullable = false)
    var consentedAt: LocalDateTime = consentedAt
        protected set

    @Comment("출시 안내를 보낸 일시 (보유 기간 계산 기준)")
    @Column(name = "notified_at")
    var notifiedAt: LocalDateTime? = null
        protected set

    fun reconsent(consentVersion: String, now: LocalDateTime) {
        this.consentVersion = validateConsentVersion(consentVersion)
        consentedAt = now
    }

    companion object {
        const val EMAIL_MAX_LENGTH = 254
        const val CONSENT_VERSION_MAX_LENGTH = 30

        /** 출시 안내를 보낸 뒤 이메일을 들고 있는 기간. 반송·문의 대응 여유분이다. */
        const val RETENTION_DAYS = 30L

        // 실제 전달 가능 여부는 메일을 보내 봐야 안다. 여기서는 오타 수준만 거르되, 문자는 흔히 쓰는 것만 받는다.
        // RFC가 허용하는 `,`·`"`·`=` 같은 문자를 받으면 어드민이 내보내는 CSV의 열이 밀리고 엑셀 수식이 실행될 수 있다.
        private val EMAIL_PATTERN = Regex("^[a-z0-9._%+-]+@[a-z0-9-]+(\\.[a-z0-9-]+)*\\.[a-z]{2,}$")

        fun request(
            email: String,
            platform: LaunchPlatform,
            consentVersion: String,
            now: LocalDateTime,
        ): LaunchNotificationRequest = LaunchNotificationRequest(
            email = normalizeEmail(email),
            platform = platform,
            consentVersion = validateConsentVersion(consentVersion),
            consentedAt = now,
        )

        /** 같은 주소를 대소문자·앞뒤 공백만 바꿔 여러 번 등록하지 못하게 저장 전에 맞춘다. */
        fun normalizeEmail(email: String): String {
            val normalized = email.trim().lowercase()
            if (normalized.length > EMAIL_MAX_LENGTH || !EMAIL_PATTERN.matches(normalized)) {
                throw WarnException(ErrorCode.BAD_REQUEST, "이메일 형식이 올바르지 않습니다.")
            }
            return normalized
        }

        /** 동의는 신청과 재신청 모두의 전제다. 체크박스를 끈 채 보낸 요청은 저장하지 않는다. */
        fun requireConsent(privacyConsent: Boolean?) {
            if (privacyConsent != true) {
                throw WarnException(ErrorCode.BAD_REQUEST, "개인정보 수집·이용에 동의해야 신청할 수 있습니다.")
            }
        }

        fun retentionThreshold(now: LocalDateTime): LocalDateTime = now.minusDays(RETENTION_DAYS)

        private fun validateConsentVersion(consentVersion: String): String {
            val trimmed = consentVersion.trim()
            if (trimmed.isEmpty() || trimmed.length > CONSENT_VERSION_MAX_LENGTH) {
                throw WarnException(ErrorCode.BAD_REQUEST, "동의 문구 버전이 올바르지 않습니다.")
            }
            return trimmed
        }
    }
}
