package com.ditto.domain.notification.entity

/** 알림을 푸시로 보내지 않고 건너뛴 이유. */
enum class PushSkipReason(
    val description: String,
) {
    MUTED("채팅방 알림 꺼짐"),
    TOGGLE_OFF("알림 설정 꺼짐"),
    NO_DEVICE("등록된 기기 없음"),
}
