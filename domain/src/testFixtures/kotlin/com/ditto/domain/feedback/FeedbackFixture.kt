package com.ditto.domain.feedback

import com.ditto.domain.feedback.entity.Feedback
import com.ditto.domain.feedback.entity.FeedbackDeviceInfo
import com.ditto.domain.feedback.entity.FeedbackType
import com.ditto.domain.withId

object FeedbackFixture {

    fun create(
        memberId: Long = 1L,
        type: FeedbackType = FeedbackType.BUG,
        content: String = "채팅방에서 사진이 안 보여요.",
        deviceInfo: FeedbackDeviceInfo = FeedbackDeviceInfo(),
        id: Long = 0L,
    ): Feedback = Feedback.receive(
        memberId = memberId,
        type = type,
        content = content,
        deviceInfo = deviceInfo,
    ).withId(id)
}
