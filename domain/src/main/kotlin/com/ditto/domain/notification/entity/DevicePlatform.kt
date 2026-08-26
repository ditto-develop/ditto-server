package com.ditto.domain.notification.entity

/** 디바이스 토큰이 발급된 기기의 플랫폼. 발송 로직은 토큰만 쓰고, 이 값은 통계·문제 추적용이다. */
enum class DevicePlatform {
    IOS,
    ANDROID,
}
