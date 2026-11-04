package com.ditto.api.config

import net.javacrumbs.shedlock.core.LockProvider
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

/**
 * 스케줄러 중복 실행 방지 락의 저장소(#361).
 *
 * 배포 중에는 기존·새 태스크가 함께 떠 있어, 락이 없으면 그 사이 걸린 주 1회 작업(퀴즈 오픈 푸시·매칭 등)이
 * 두 번 돈다. 락은 `shedlock` 테이블에 만료 시각을 적는 방식이라 작업 동안 DB 연결·트랜잭션을 붙잡지 않는다 —
 * 매칭처럼 퀴즈셋마다 트랜잭션을 나누는 작업(ADR 0027)과 부딪히지 않는다.
 *
 * 시각 비교는 DB 시각으로 한다(`usingDbTime`). 인스턴스마다 시계가 조금씩 달라도 같은 기준으로 만료를 본다.
 *
 * 락을 거는 `@EnableSchedulerLock`은 [SchedulingConfig]에 있어 테스트에서는 꺼져 있다. 이 빈은 테스트에도
 * 떠서 테이블과 락 동작을 검증할 수 있다.
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
