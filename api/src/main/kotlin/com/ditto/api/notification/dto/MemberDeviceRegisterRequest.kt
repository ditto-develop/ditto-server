package com.ditto.api.notification.dto

import com.ditto.domain.notification.entity.DevicePlatform
import com.ditto.domain.notification.entity.MemberDevice
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * 디바이스 토큰 등록 요청. [token]은 앱이 FCM 에서 받은 등록 토큰 그대로다 —
 * 서버는 형식을 해석하지 않으므로 길이·공백만 검증한다.
 */
data class MemberDeviceRegisterRequest(
    @field:NotBlank
    @field:Size(max = MemberDevice.TOKEN_MAX_LENGTH)
    val token: String,

    val platform: DevicePlatform,
)
