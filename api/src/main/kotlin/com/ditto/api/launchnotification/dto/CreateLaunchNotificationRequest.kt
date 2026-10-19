package com.ditto.api.launchnotification.dto

import com.ditto.common.logging.Mask
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

/**
 * 출시 알림 신청 요청. 형식·길이 검증은 정규화(trim·소문자) 뒤에 도메인이 한다 — 앞뒤 공백이 붙은 정상 주소를 거부하지 않게.
 */
data class CreateLaunchNotificationRequest(
    @field:Mask
    @field:NotBlank
    val email: String,

    @field:NotBlank
    val platform: String,

    @field:NotNull
    val privacyConsent: Boolean?,

    @field:NotBlank
    val consentVersion: String,
)
