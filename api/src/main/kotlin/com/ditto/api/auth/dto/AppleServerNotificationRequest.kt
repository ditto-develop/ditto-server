package com.ditto.api.auth.dto

import jakarta.validation.constraints.NotBlank

/** 애플이 보내는 서버 간 알림 본문. payload 는 애플이 서명한 JWS 다. */
data class AppleServerNotificationRequest(
    @field:NotBlank
    val payload: String,
)
