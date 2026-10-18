package com.ditto.api.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** 어드민 QA 도구 스위치. 무엇을 끄는지는 ADR 0039. */
@ConfigurationProperties(prefix = "ditto.admin.qa-tools")
data class AdminQaToolsProperties(
    val enabled: Boolean = false,
)
