package com.ditto.api.setting.dto

/**
 * 알림 설정 부분 패치. **null = 변경 없음**이다 —
 * 화면은 토글 하나를 누를 때마다 그 항목만 보낸다.
 */
data class UpdateNotificationSettingsRequest(
    val matching: Boolean? = null,
    val chat: Boolean? = null,
    val marketing: Boolean? = null,
)
