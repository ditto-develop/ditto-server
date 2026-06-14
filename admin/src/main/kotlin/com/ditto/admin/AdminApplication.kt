package com.ditto.admin

import com.ditto.application.config.ApplicationConfig
import com.ditto.domain.config.DomainConfig
import com.ditto.infrastructure.oauth.config.OAuthConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import
import java.util.TimeZone

/**
 * 운영자용 어드민 애플리케이션(Thymeleaf 서버 렌더링). 메인 서비스와 동일한 카카오 로그인을 쓰되,
 * role=ADMIN 회원만 접근할 수 있다. 별도 ECS 서비스로 배포된다.
 */
@SpringBootApplication(scanBasePackages = ["com.ditto.admin"])
@ConfigurationPropertiesScan(basePackages = ["com.ditto.admin"])
@Import(
    DomainConfig::class,
    OAuthConfig::class,
    ApplicationConfig::class,
)
class AdminApplication

fun main(args: Array<String>) {
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))
    runApplication<AdminApplication>(*args)
}
