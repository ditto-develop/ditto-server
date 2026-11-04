package com.ditto.api.config

import com.ditto.api.support.IntegrationTest
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import net.javacrumbs.shedlock.core.LockConfiguration
import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.springframework.aop.support.AopUtils
import org.springframework.context.ApplicationContext
import org.springframework.scheduling.annotation.Scheduled
import java.time.Duration
import java.time.Instant
import javax.sql.DataSource

class SchedulerLockTest(
    private val lockProvider: LockProvider,
    private val applicationContext: ApplicationContext,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun lockConfig(name: String, atLeast: Duration = Duration.ZERO) =
        LockConfiguration(Instant.now(), name, Duration.ofMinutes(5), atLeast)

    "락 저장소" - {
        "한 인스턴스가 잡고 있는 동안에는 다른 쪽이 같은 이름의 락을 잡지 못한다" {
            val held = lockProvider.lock(lockConfig("matching")).orElse(null)
            held.shouldNotBeNull()

            lockProvider.lock(lockConfig("matching")).orElse(null).shouldBeNull()
        }

        "작업이 빨리 끝나도 최소 보유 시간 동안은 같은 회차를 다시 잡지 못한다" {
            lockProvider.lock(lockConfig("quiz-opened-notification", atLeast = Duration.ofMinutes(1))).get().unlock()

            lockProvider.lock(lockConfig("quiz-opened-notification")).orElse(null).shouldBeNull()
        }

        "최소 보유 시간 없이 풀린 락은 바로 다시 잡을 수 있다" {
            lockProvider.lock(lockConfig("chat-room-lifecycle")).get().unlock()

            lockProvider.lock(lockConfig("chat-room-lifecycle")).orElse(null).shouldNotBeNull()
        }
    }

    "스케줄러" - {
        "모든 크론 메서드에 서로 다른 이름의 락이 걸려 있다" {
            val scheduled = applicationContext.beanDefinitionNames
                .map { AopUtils.getTargetClass(applicationContext.getBean(it)) }
                .filter { it.name.startsWith("com.ditto") }
                .flatMap { type -> type.declaredMethods.filter { it.isAnnotationPresent(Scheduled::class.java) } }
            scheduled.shouldNotBeEmpty()

            scheduled.filter { !it.isAnnotationPresent(SchedulerLock::class.java) }
                .map { "${it.declaringClass.simpleName}.${it.name}" }
                .shouldBeEmpty()

            scheduled.mapNotNull { it.getAnnotation(SchedulerLock::class.java)?.name }
                .groupingBy { it }.eachCount().filterValues { it > 1 }.keys
                .shouldBeEmpty()
        }
    }
})
