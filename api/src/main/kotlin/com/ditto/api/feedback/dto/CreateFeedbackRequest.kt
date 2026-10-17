package com.ditto.api.feedback.dto

import com.ditto.domain.feedback.entity.Feedback
import com.ditto.domain.feedback.entity.FeedbackDeviceInfo
import com.ditto.domain.feedback.entity.FeedbackImage
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateFeedbackRequest(
    @field:NotBlank(message = "의견 유형이 필요합니다.")
    val type: String,

    @field:NotBlank(message = "내용을 입력해 주세요.")
    @field:Size(max = Feedback.CONTENT_MAX_LENGTH, message = "내용이 너무 깁니다.")
    val content: String,

    @field:Size(max = Feedback.APP_VERSION_MAX_LENGTH, message = "앱 버전이 너무 깁니다.")
    val appVersion: String? = null,

    @field:Size(max = Feedback.OS_MAX_LENGTH, message = "OS 정보가 너무 깁니다.")
    val os: String? = null,

    @field:Size(max = Feedback.DEVICE_MODEL_MAX_LENGTH, message = "기기 모델이 너무 깁니다.")
    val deviceModel: String? = null,

    @field:Size(max = FeedbackImage.MAX_COUNT, message = "이미지 첨부는 최대 3장까지 가능합니다.")
    val imageKeys: List<String> = emptyList(),
) {
    fun toDeviceInfo(): FeedbackDeviceInfo =
        FeedbackDeviceInfo(appVersion = appVersion, os = os, deviceModel = deviceModel)
}
