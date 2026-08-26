package com.ditto.api.notification.dto

import com.ditto.common.logging.Mask
import com.ditto.domain.notification.entity.DevicePlatform
import com.ditto.domain.notification.entity.MemberDevice
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * [token]은 FCM 이 준 그대로 — 형식은 검증하지 않고 길이·공백만 본다.
 * 로그에는 남기지 않는다([Mask]). 등록이 소유 증명 없이 소유권을 넘기므로 토큰이 새면 푸시를 뺏긴다.
 */
data class MemberDeviceRegisterRequest(
    @field:Mask
    @field:NotBlank
    @field:Size(max = MemberDevice.TOKEN_MAX_LENGTH)
    val token: String,

    val platform: DevicePlatform,
)
