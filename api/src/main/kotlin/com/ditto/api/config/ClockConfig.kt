package com.ditto.api.config

import com.ditto.common.support.ServerClock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ClockConfig {
    @Bean
    fun serverClock() = ServerClock()
}
