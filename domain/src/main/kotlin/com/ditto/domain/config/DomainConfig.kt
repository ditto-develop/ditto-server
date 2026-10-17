package com.ditto.domain.config

import com.ditto.domain.chat.converter.ChatContentCipher
import com.querydsl.jpa.impl.JPAQueryFactory
import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Value
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

    // 하이버네이트가 스프링 빈 컨테이너로 컨버터를 만들어서 생성자로 주입된다.
    @Bean
    open fun chatContentCipher(@Value("\${ditto.chat.content-encryption-key}") base64Key: String) =
        ChatContentCipher.fromBase64Key(base64Key)
}
