package com.ditto.api.system

import com.ditto.domain.system.entity.ServerTimeOverride
import com.ditto.domain.system.repository.ServerTimeOverrideRepository
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.LocalDateTime

class ServerTimeServiceTest : FreeSpec({
    "오버라이드가 없으면 비활성 기본값을 반환한다" {
        val repository = mockk<ServerTimeOverrideRepository>(relaxed = true)
        every { repository.findFirstByOrderByIdAsc() } returns null

        ServerTimeService(repository).getOverride().enabled shouldBe false
    }

    "override 는 시각·변경자를 설정하고 저장한다" {
        val repository = mockk<ServerTimeOverrideRepository>(relaxed = true)
        every { repository.findFirstByOrderByIdAsc() } returns null
        val saved = slot<ServerTimeOverride>()
        every { repository.save(capture(saved)) } answers { saved.captured }
        val dateTime = LocalDateTime.of(2026, 6, 18, 9, 0)

        ServerTimeService(repository).override(dateTime, "관리자", "admin@ditto.pics")

        saved.captured.enabled shouldBe true
        saved.captured.overrideDateTime shouldBe dateTime
        saved.captured.authorName shouldBe "관리자"
        saved.captured.authorEmail shouldBe "admin@ditto.pics"
    }

    "disable 은 기존 오버라이드가 있으면 해제 후 저장한다" {
        val repository = mockk<ServerTimeOverrideRepository>(relaxed = true)
        val existing = ServerTimeOverride.disabled().apply { override(LocalDateTime.now(), "관리자", "admin@ditto.pics") }
        every { repository.findFirstByOrderByIdAsc() } returns existing
        every { repository.save(existing) } returns existing

        ServerTimeService(repository).disable()

        existing.enabled shouldBe false
        verify { repository.save(existing) }
    }

    "disable 은 오버라이드가 없으면 아무것도 저장하지 않는다" {
        val repository = mockk<ServerTimeOverrideRepository>(relaxed = true)
        every { repository.findFirstByOrderByIdAsc() } returns null

        ServerTimeService(repository).disable()

        verify(exactly = 0) { repository.save(any()) }
    }
})
