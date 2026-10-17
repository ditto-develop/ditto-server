package com.ditto.domain.feedback.entity

enum class FeedbackStatus(val description: String) {
    RECEIVED("확인 대기"),
    COMPLETED("처리 완료"),
}
