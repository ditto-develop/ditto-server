package com.ditto.api.system

import com.ditto.api.config.AdminQaToolsProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component

/** QA 도구를 끈 채 서버가 뜨면 남은 시간 조정을 해제한다. 나중에 스위치를 다시 켜도 예전 시각이 살아나지 않는다. */
@Component
class LeftoverTimeOverrideDisabler(
    private val adminQaToolsProperties: AdminQaToolsProperties,
    private val serverTimeService: ServerTimeService,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        if (adminQaToolsProperties.enabled) return
        if (!serverTimeService.getOverride().enabled) return

        log.info { "QA 도구가 꺼져 있어 남은 시간 조정을 해제함" }
        serverTimeService.disable()
    }

    companion object {
        private val log = KotlinLogging.logger {}
    }
}
