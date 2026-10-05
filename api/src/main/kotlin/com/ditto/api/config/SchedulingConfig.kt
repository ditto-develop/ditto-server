package com.ditto.api.config

import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.scheduling.config.ScheduledTaskRegistrar

/**
 * WebSocket 설정이 `TaskScheduler` 빈을 따로 등록해서 Boot 자동설정의 `taskScheduler`가 만들어지지 않는다.
 * 그러면 크론이 스레드 하나짜리 기본 실행기에서 돌고 `spring.task.scheduling` 설정도 먹지 않는다.
 * 자동설정과 같은 빌더로 직접 만들어 그 설정을 따르게 한다.
 */
@Configuration
@EnableScheduling
@Profile("!test")
class SchedulingConfig(
    private val taskSchedulerBuilder: ThreadPoolTaskSchedulerBuilder,
) : SchedulingConfigurer {

    override fun configureTasks(registrar: ScheduledTaskRegistrar) {
        registrar.setTaskScheduler(cronTaskScheduler())
    }

    @Bean
    fun cronTaskScheduler(): ThreadPoolTaskScheduler = taskSchedulerBuilder.build()
}
