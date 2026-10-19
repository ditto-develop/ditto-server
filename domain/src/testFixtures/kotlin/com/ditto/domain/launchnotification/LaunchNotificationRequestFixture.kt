package com.ditto.domain.launchnotification

import com.ditto.domain.launchnotification.entity.LaunchNotificationRequest
import com.ditto.domain.launchnotification.entity.LaunchPlatform
import com.ditto.domain.withId
import java.time.LocalDateTime

object LaunchNotificationRequestFixture {

    fun create(
        email: String = "visitor@example.com",
        platform: LaunchPlatform = LaunchPlatform.ANDROID,
        consentVersion: String = "2026-10-19",
        now: LocalDateTime = LocalDateTime.of(2026, 10, 19, 12, 0),
        id: Long = 0L,
    ): LaunchNotificationRequest = LaunchNotificationRequest.request(
        email = email,
        platform = platform,
        consentVersion = consentVersion,
        now = now,
    ).withId(id)
}
