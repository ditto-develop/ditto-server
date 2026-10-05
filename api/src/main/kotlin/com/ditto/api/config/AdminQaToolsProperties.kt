package com.ditto.api.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** 운영 데이터를 지우는 어드민 QA 도구의 스위치. 실제 서비스 시작 전에 끈다. */
@ConfigurationProperties(prefix = "ditto.admin.qa-tools")
data class AdminQaToolsProperties(
    val enabled: Boolean = false,
)
