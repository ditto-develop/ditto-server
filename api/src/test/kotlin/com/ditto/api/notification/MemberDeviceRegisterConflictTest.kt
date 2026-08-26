package com.ditto.api.notification

import com.ditto.api.notification.facade.MemberDeviceFacade
import com.ditto.api.notification.service.MemberDeviceService
import com.ditto.domain.notification.entity.DevicePlatform
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.dao.DataIntegrityViolationException

/** 동시 최초 등록(따닥)에서 유일 제약에 진 쪽의 응답. 트랜잭션 경쟁은 실제 입력으로 못 만들어 mock 을 쓴다. */
class MemberDeviceRegisterConflictTest {

    private val memberDeviceService = mockk<MemberDeviceService>()
    private val memberDeviceFacade = MemberDeviceFacade(memberDeviceService)

    @Test
    @DisplayName("유일 제약에 걸리면 오류가 아니라 registered=false 다 — 첫 요청이 이미 등록했다")
    fun uniqueViolationAnsweredAsNotRegistered() {
        every { memberDeviceService.register(1L, "token", DevicePlatform.ANDROID) } throws
            DataIntegrityViolationException("duplicate key")

        val registered = memberDeviceFacade.register(1L, "token", DevicePlatform.ANDROID)

        registered shouldBe false
    }

    @Test
    @DisplayName("유일 제약이 아닌 실패는 그대로 던진다")
    fun otherFailurePropagates() {
        every { memberDeviceService.register(1L, "token", DevicePlatform.ANDROID) } throws
            DataAccessResourceFailureException("db down")

        shouldThrow<DataAccessResourceFailureException> {
            memberDeviceFacade.register(1L, "token", DevicePlatform.ANDROID)
        }
    }
}
