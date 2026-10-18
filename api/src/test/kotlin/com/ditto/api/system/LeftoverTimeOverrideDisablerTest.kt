package com.ditto.api.system

import com.ditto.api.config.AdminQaToolsProperties
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.system.repository.ServerTimeOverrideRepository
import io.kotest.matchers.shouldBe
import org.springframework.boot.DefaultApplicationArguments
import java.time.LocalDateTime
import javax.sql.DataSource

class LeftoverTimeOverrideDisablerTest(
    private val serverTimeService: ServerTimeService,
    private val serverTimeOverrideRepository: ServerTimeOverrideRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun runOnStartup(qaToolsEnabled: Boolean) {
        LeftoverTimeOverrideDisabler(AdminQaToolsProperties(enabled = qaToolsEnabled), serverTimeService)
            .run(DefaultApplicationArguments())
    }

    fun isOverrideEnabled(): Boolean = serverTimeOverrideRepository.findFirstByOrderByIdAsc()?.enabled ?: false

    "서버가 뜰 때" - {
        "QA 도구가 꺼져 있으면 남은 시간 조정을 해제한다" {
            serverTimeService.override(LocalDateTime.of(2026, 6, 18, 9, 0), "관리자", "admin@ditto.pics")

            runOnStartup(qaToolsEnabled = false)

            isOverrideEnabled() shouldBe false
        }

        "QA 도구가 켜져 있으면 시간 조정을 그대로 둔다" {
            serverTimeService.override(LocalDateTime.of(2026, 6, 18, 9, 0), "관리자", "admin@ditto.pics")

            runOnStartup(qaToolsEnabled = true)

            isOverrideEnabled() shouldBe true
        }

        "시간 조정 기록이 없으면 아무것도 만들지 않는다" {
            runOnStartup(qaToolsEnabled = false)

            serverTimeOverrideRepository.count() shouldBe 0
        }
    }
})
