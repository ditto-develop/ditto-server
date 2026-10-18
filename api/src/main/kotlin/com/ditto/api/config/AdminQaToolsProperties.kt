package com.ditto.api.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** 어드민 QA 도구(퀴즈셋 초기화·더미·QA 콘솔·시간 조정)의 스위치. */
@ConfigurationProperties(prefix = "ditto.admin.qa-tools")
data class AdminQaToolsProperties(
    val enabled: Boolean = false,
)
