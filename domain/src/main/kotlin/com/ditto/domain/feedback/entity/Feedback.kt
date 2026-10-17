package com.ditto.domain.feedback.entity

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
import org.hibernate.annotations.Comment

@Entity
@Table(
    name = "feedback",
    indexes = [
        Index(name = "feedback_index_1", columnList = "status, created_at"),
    ],
)
class Feedback private constructor(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Comment("보낸 회원 ID")
    @Column(name = "member_id", nullable = false)
    val memberId: Long,

    @Comment("의견 유형")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val type: FeedbackType,

    @Comment("내용")
    @Column(nullable = false, length = CONTENT_MAX_LENGTH)
    val content: String,

    @Comment("앱 버전")
    @Column(name = "app_version", length = APP_VERSION_MAX_LENGTH)
    val appVersion: String? = null,

    @Comment("OS 이름과 버전")
    @Column(length = OS_MAX_LENGTH)
    val os: String? = null,

    @Comment("기기 모델")
    @Column(name = "device_model", length = DEVICE_MODEL_MAX_LENGTH)
    val deviceModel: String? = null,

    @Comment("처리 상태")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val status: FeedbackStatus = FeedbackStatus.RECEIVED,
) : BaseEntity() {

    companion object {
        const val CONTENT_MAX_LENGTH = 1000
        const val APP_VERSION_MAX_LENGTH = 30
        const val OS_MAX_LENGTH = 30
        const val DEVICE_MODEL_MAX_LENGTH = 50

        fun receive(
            memberId: Long,
            type: FeedbackType,
            content: String,
            deviceInfo: FeedbackDeviceInfo = FeedbackDeviceInfo(),
        ): Feedback {
            if (content.isBlank()) {
                throw WarnException(ErrorCode.BAD_REQUEST, "내용을 입력해 주세요.")
            }
            return Feedback(
                memberId = memberId,
                type = type,
                content = content,
                appVersion = deviceInfo.appVersion,
                os = deviceInfo.os,
                deviceModel = deviceInfo.deviceModel,
            )
        }
    }
}
