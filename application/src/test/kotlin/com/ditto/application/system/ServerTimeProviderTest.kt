package com.ditto.application.system

import com.ditto.domain.system.entity.ServerTimeOverride
import com.ditto.domain.system.repository.ServerTimeOverrideRepository
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDateTime

class ServerTimeProviderTest : FreeSpec({
    val repository = mockk<ServerTimeOverrideRepository>()
    val provider = ServerTimeProvider(repository)

    "오버라이드가 없으면 실제 시각을 반환한다" {
        every { repository.findFirstByOrderByIdAsc() } returns null
        val before = LocalDateTime.now()

        val now = provider.now()

        (now >= before) shouldBe true
    }

    "오버라이드가 활성이면 설정된 시각을 반환한다" {
        val overridden = LocalDateTime.of(2026, 6, 18, 9, 0)
        val override = ServerTimeOverride.disabled().apply { override(overridden, "관리자", "admin@ditto.pics") }
        every { repository.findFirstByOrderByIdAsc() } returns override

        provider.now() shouldBe overridden
    }

    "오버라이드가 비활성이면 실제 시각을 반환한다" {
        val override = ServerTimeOverride.disabled().apply {
            override(LocalDateTime.of(2020, 1, 1, 0, 0), null, null)
            disable()
        }
        every { repository.findFirstByOrderByIdAsc() } returns override
        val before = LocalDateTime.now()

        (provider.now() >= before) shouldBe true
    }
})
