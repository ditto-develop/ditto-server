package com.ditto.api.support

import com.ditto.common.support.ServerClock
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId

@TestConfiguration
class TestClockConfig {

    @Bean
    @Primary
    fun fixedServerClock(): ServerClock {
        val fixedInstant = FIXED_TIME.atZone(ZoneId.systemDefault()).toInstant()
        return ServerClock(Clock.fixed(fixedInstant, ZoneId.systemDefault()))
    }

    companion object {
        val FIXED_TIME: LocalDateTime = LocalDateTime.of(2026, 4, 10, 12, 0, 0)
    }
}
