package com.ditto.api.config

import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock
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
 * 자동설정과 같은 빌더로 직접 만들어 그 설정을 따르게 한다(종료 시 실행 중인 작업 대기도 이 설정이다).
 *
 * 크론은 모두 `@SchedulerLock`으로 한 인스턴스에서만 돈다(ADR 0049). 보유 시간은 [SchedulerLockDurations],
 * 저장소는 [SchedulerLockConfig]. 테스트에서는 스케줄링을 켜지 않아 락도 걸리지 않는다 — 테스트는 스케줄러 메서드를
 * 직접 부르는데, 락이 걸리면 같은 테스트 안의 두 번째 호출이 최소 보유 시간에 막혀 조용히 건너뛴다.
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = SchedulerLockDurations.DEFAULT_AT_MOST)
@Profile("!test")
class SchedulingConfig(
    private val taskSchedulerBuilder: ThreadPoolTaskSchedulerBuilder,
) : SchedulingConfigurer {

    override fun configureTasks(registrar: ScheduledTaskRegistrar) {
        registrar.setTaskScheduler(cronTaskScheduler())
    }

    /**
     * 종료할 때 실행 중인 작업은 기다리고(`spring.task.scheduling.shutdown.await-termination`), 예약만 된 다음 회차는
     * 큐에서 지운다. 종료 때 취소된 회차도 예약 시각까지 큐에 남아, 지우지 않으면 실행 중인 작업이 없어도 대기 시간을
     * 다 쓴다(로컬에서 종료 21초 → 1초).
     */
    @Bean
    fun cronTaskScheduler(): ThreadPoolTaskScheduler = taskSchedulerBuilder.build().apply {
        setExecuteExistingDelayedTasksAfterShutdownPolicy(false)
    }
}
