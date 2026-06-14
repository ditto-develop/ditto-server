package com.ditto.application

import com.ditto.domain.config.DomainConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Import

/**
 * application 모듈 통합테스트용 부팅 설정.
 * com.ditto.application 스캔으로 공용 서비스 빈(ApplicationConfig 포함)을 올리고, 도메인 JPA([DomainConfig])를 가져온다.
 */
@SpringBootApplication(scanBasePackages = ["com.ditto.application"])
@Import(DomainConfig::class)
class TestApplication
