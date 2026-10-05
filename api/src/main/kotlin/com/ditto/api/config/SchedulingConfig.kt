package com.ditto.api.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.scheduling.config.ScheduledTaskRegistrar

/**
 * 크론은 STOMP 하트비트 스케줄러(WebSocketConfig)와 따로 돈다. 같은 스레드를 쓰면 오래 걸리는 크론이
 * 하트비트를 막아 WebSocket 연결이 끊긴다.
 *
 * 스레드를 하나로 두어 크론끼리는 지금처럼 차례로 돈다. 늘리면 서로 다른 크론이 동시에 돌고
 * DB 커넥션도 그만큼 더 쓴다.
 */
@Configuration
@EnableScheduling
@Profile("!test")
class SchedulingConfig : SchedulingConfigurer {

    override fun configureTasks(registrar: ScheduledTaskRegistrar) {
        registrar.setTaskScheduler(cronTaskScheduler())
    }

    @Bean
    fun cronTaskScheduler(): ThreadPoolTaskScheduler =
        ThreadPoolTaskScheduler().apply {
            poolSize = 1
            setThreadNamePrefix("cron-")
        }
}
