package com.ditto.domain.notification.entity

/** 알림의 `target_id`가 가리키는 대상. 대상이 지워질 때 그 대상을 가리키는 알림을 함께 찾는 기준이다. */
enum class NotificationTarget {
    QUIZ_SET,
    CHAT_ROOM,
    PERSONAL_MATCH,
    GROUP_MATCH,
    REMATCH,
    SYSTEM_NOTICE,
    MEMBER_REPORT,
    SANCTION,
}
