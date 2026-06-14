package com.ditto.application.system

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDateTime

class SystemStateProviderTest : FreeSpec({
    val serverTimeProvider = mockk<ServerTimeProvider>()
    val provider = SystemStateProvider(serverTimeProvider)

    "현재 시각으로부터 연/월/기간을 계산한다 (목요일 → MATCHING)" {
        // 2026-06-18 은 목요일
        every { serverTimeProvider.now() } returns LocalDateTime.of(2026, 6, 18, 10, 0)

        val state = provider.current()

        state.year shouldBe 2026
        state.month shouldBe 6
        state.period shouldBe SystemPeriod.MATCHING_PERIOD
    }

    "월요일은 QUIZ 기간" {
        // 2026-06-15 는 월요일
        every { serverTimeProvider.now() } returns LocalDateTime.of(2026, 6, 15, 10, 0)

        provider.current().period shouldBe SystemPeriod.QUIZ_PERIOD
    }

    "토요일은 CHATTING 기간" {
        // 2026-06-20 은 토요일
        every { serverTimeProvider.now() } returns LocalDateTime.of(2026, 6, 20, 10, 0)

        provider.current().period shouldBe SystemPeriod.CHATTING_PERIOD
    }
})
