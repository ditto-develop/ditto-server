package com.ditto.domain.notification

import com.ditto.domain.notification.entity.SystemNotice
import com.ditto.domain.withId

object SystemNoticeFixture {

    fun create(
        title: String = "ditto가 업데이트됐어요",
        body: String? = "이번에 달라진 점을 확인해보세요.",
        authorMemberId: Long = 1L,
        authorName: String? = "관리자",
        authorEmail: String? = "admin@ditto.pics",
        id: Long = 0L,
    ): SystemNotice = SystemNotice.create(
        title = title,
        body = body,
        authorMemberId = authorMemberId,
        authorName = authorName,
        authorEmail = authorEmail,
    ).withId(id)
}
