package com.ditto.api.setting.dto

import com.ditto.domain.member.entity.MemberNotificationSetting

/**
 * 설정 화면(피그마 6.2)의 알림 토글 3종. 항목 이름은 화면 라벨을 그대로 따른다 —
 * 매칭 알림 / 채팅 알림 / 마케팅 정보 수신.
 */
data class NotificationSettingsResponse(
    val matching: Boolean,
    val chat: Boolean,
    val marketing: Boolean,
)

fun MemberNotificationSetting.toResponse() = NotificationSettingsResponse(
    matching = matching,
    chat = chat,
    marketing = marketing,
)
