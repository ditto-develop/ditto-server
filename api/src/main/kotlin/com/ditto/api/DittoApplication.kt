package com.ditto.api

import com.ditto.domain.config.DomainConfig
import com.ditto.infrastructure.fcm.config.FcmConfig
import com.ditto.infrastructure.oauth.config.OAuthConfig
import com.ditto.infrastructure.storage.config.StorageConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import
import java.util.TimeZone

/**
 * 인증 주체는 JWT(`JwtAuthenticationFilter`)와 어드민 세션(`AdminSecurityConfig`)이 직접 만든다 —
 * `UserDetailsService` 를 쓰는 경로가 없다. 자동설정을 켜 두면 부팅마다 인메모리 계정 하나와
 * 생성 비밀번호가 로그에 찍히는데("Using generated security password"), 프로덕션 로그에 남길 값이 아니다.
 */
@SpringBootApplication(
    scanBasePackages = ["com.ditto.api"],
    exclude = [UserDetailsServiceAutoConfiguration::class],
)
@ConfigurationPropertiesScan(basePackages = ["com.ditto.api"])
@Import(
    DomainConfig::class,
    FcmConfig::class,
    OAuthConfig::class,
    StorageConfig::class,
)
class DittoApplication

fun main(args: Array<String>) {
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))
    runApplication<DittoApplication>(*args)
}
