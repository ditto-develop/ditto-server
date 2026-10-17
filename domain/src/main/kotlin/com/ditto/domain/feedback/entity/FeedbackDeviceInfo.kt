package com.ditto.domain.feedback.entity

/** 의견을 보낸 앱 환경. FE가 모를 수 있어 전부 선택값이다. */
data class FeedbackDeviceInfo(
    val appVersion: String? = null,
    val os: String? = null,
    val deviceModel: String? = null,
)
