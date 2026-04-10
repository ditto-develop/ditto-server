package com.ditto.api.support

import com.ditto.common.support.ServerClock
import org.springframework.context.annotation.Bean
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Primary
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

@TestConfiguration
class TestClockConfig {

    @Bean
    @Primary
    fun fixedServerClock(): ServerClock {
        val fixedInstant = FIXED_TIME.toInstant(ZoneOffset.ofHours(9))
        return ServerClock(Clock.fixed(fixedInstant, KST))
    }

    companion object {
        private val KST = ZoneId.of("Asia/Seoul")
        val FIXED_TIME: LocalDateTime = LocalDateTime.of(2026, 4, 10, 12, 0, 0)
    }
}
