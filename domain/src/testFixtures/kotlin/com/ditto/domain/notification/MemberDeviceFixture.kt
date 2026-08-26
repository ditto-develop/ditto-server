package com.ditto.domain.notification

import com.ditto.domain.notification.entity.DevicePlatform
import com.ditto.domain.notification.entity.MemberDevice
import com.ditto.domain.withId

object MemberDeviceFixture {

    fun create(
        memberId: Long = 1L,
        token: String = "fcm-token-1",
        platform: DevicePlatform = DevicePlatform.ANDROID,
        id: Long = 0L,
    ): MemberDevice = MemberDevice.create(
        memberId = memberId,
        token = token,
        platform = platform,
    ).withId(id)
}
