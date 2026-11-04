package com.ditto.api.config

import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

/**
 * 크론 중복 실행 방지 락의 저장소(ADR 0049). `shedlock` 테이블에 작업 이름마다 만료 시각을 적는다.
 *
 * 만료 비교는 DB 시각으로 한다(`usingDbTime`) — 인스턴스마다 시계가 조금씩 달라도 같은 기준으로 본다.
 * 락을 거는 `@EnableSchedulerLock`은 [SchedulingConfig]에 있어 테스트에서는 꺼지지만, 이 빈은 테스트에도 떠서
 * 테이블과 락 동작을 검증할 수 있다.
 */
@Configuration
class SchedulerLockConfig {

    @Bean
    fun lockProvider(dataSource: DataSource): LockProvider = JdbcTemplateLockProvider(
        JdbcTemplateLockProvider.Configuration.builder()
            .withJdbcTemplate(JdbcTemplate(dataSource))
            .usingDbTime()
            .build(),
    )
}
