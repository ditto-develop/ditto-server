package com.ditto.domain.notification.entity

/** 공지를 보낸 어드민. 이름·이메일은 발송 시점 스냅샷이다. */
data class SystemNoticeAuthor(
    val memberId: Long,
    val name: String?,
    val email: String?,
)
