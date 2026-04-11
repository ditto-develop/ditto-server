package com.ditto.domain.config

import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@Configuration
@EnableJpaAuditing
@EntityScan("com.ditto.domain")
@EnableJpaRepositories("com.ditto.domain")
open class DomainConfig {

    @Bean
    open fun jpaQueryFactory(entityManager: EntityManager) = JPAQueryFactory(entityManager)
}
