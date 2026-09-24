package com.ditto.domain.notification

import com.ditto.domain.notification.entity.SystemNotice
import com.ditto.domain.notification.entity.SystemNoticeAuthor
import com.ditto.domain.withId

object SystemNoticeFixture {

    fun create(
        title: String = "ditto가 업데이트됐어요",
        body: String? = "이번에 달라진 점을 확인해보세요.",
        author: SystemNoticeAuthor = SystemNoticeAuthor(memberId = 1L, name = "관리자", email = "admin@ditto.pics"),
        targetCount: Int = 10,
        id: Long = 0L,
    ): SystemNotice = SystemNotice.create(
        title = title,
        body = body,
        author = author,
        targetCount = targetCount,
    ).withId(id)
}
