package com.ditto.api.system

import com.ditto.domain.system.entity.ServerTimeOverride
import com.ditto.domain.system.repository.ServerTimeOverrideRepository
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class ServerTimeProviderTest : FreeSpec({
    val repository = mockk<ServerTimeOverrideRepository>()
    val provider = ServerTimeProvider(repository)

    "오버라이드가 없으면 실제 시각을 반환한다" {
        every { repository.findFirstByOrderByIdAsc() } returns null
        val before = LocalDateTime.now()

        val now = provider.now()

        (now >= before) shouldBe true
    }

    // 리눅스는 나노초까지 주지만 DB 컬럼은 DATETIME(6)이다. 절삭하지 않으면 방금 쓴 시각을 그대로
    // 응답하는 요청과 다시 읽어 응답하는 요청이 서로 다른 값을 내보낸다.
    "실제 시각은 DB 가 저장할 수 있는 마이크로초까지만 반환한다" {
        every { repository.findFirstByOrderByIdAsc() } returns null

        val now = provider.now()

        now.nano % 1_000 shouldBe 0
        now shouldBe now.truncatedTo(ChronoUnit.MICROS)
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
