package com.ditto.domain.notification

import com.ditto.domain.notification.entity.Notification
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.withId

object NotificationFixture {

    fun create(
        memberId: Long = 1L,
        type: NotificationType = NotificationType.MATCH_RESULT,
        title: String = "이번 주 매칭 결과가 나왔어요",
        body: String? = "나와 답변이 비슷한 사람들을 찾았어요. 지금 확인해 보세요.",
        targetId: Long? = 1L,
        id: Long = 0L,
    ): Notification = Notification.create(
        memberId = memberId,
        type = type,
        title = title,
        body = body,
        targetId = targetId,
    ).withId(id)
}
