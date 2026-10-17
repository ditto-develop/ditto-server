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
import java.time.LocalDateTime
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

    status: FeedbackStatus = FeedbackStatus.RECEIVED,
) : BaseEntity() {

    @Comment("처리 상태")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: FeedbackStatus = status
        protected set

    @Comment("처리 완료한 관리자 표시명 스냅샷")
    @Column(name = "completed_by_name", length = COMPLETED_BY_NAME_MAX_LENGTH)
    var completedByName: String? = null
        protected set

    @Comment("처리 완료 일시")
    @Column(name = "completed_at")
    var completedAt: LocalDateTime? = null
        protected set

    val isCompleted: Boolean
        get() = status == FeedbackStatus.COMPLETED

    fun complete(adminName: String, now: LocalDateTime) {
        if (isCompleted) {
            throw WarnException(ErrorCode.BAD_REQUEST, "이미 처리 완료한 의견입니다.")
        }
        status = FeedbackStatus.COMPLETED
        completedByName = adminName
        completedAt = now
    }

    companion object {
        const val CONTENT_MAX_LENGTH = 1000
        const val APP_VERSION_MAX_LENGTH = 30
        const val OS_MAX_LENGTH = 30
        const val DEVICE_MODEL_MAX_LENGTH = 50

        // 관리자 표시명은 "이름(이메일)"이라 이름 50자, 이메일 100자를 합쳐도 들어가게 둔다.
        const val COMPLETED_BY_NAME_MAX_LENGTH = 200

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
