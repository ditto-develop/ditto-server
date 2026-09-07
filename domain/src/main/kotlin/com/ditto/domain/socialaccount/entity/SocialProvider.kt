package com.ditto.domain.socialaccount.entity

enum class SocialProvider {
    KAKAO,

    /**
     * iOS 앱은 App Store 심사 지침 4.8에 따라 카카오와 함께 애플 로그인도 제공해야 한다.
     * 네이티브 흐름(ID 토큰 검증)만 지원한다 — 웹은 카카오 리다이렉트를 쓴다.
     */
    APPLE,
    ;
}
