package com.ditto.application.config

import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

/**
 * application 모듈의 공용 서비스 빈을 등록한다.
 *
 * api·admin 등 부팅 모듈이 각자 `@Import(ApplicationConfig::class)` 로 가져간다.
 * (각 부팅 모듈의 `scanBasePackages` 는 자기 패키지만 스캔하므로 명시적 import 가 필요하다.)
 */
@Configuration
@ComponentScan("com.ditto.application")
class ApplicationConfig
