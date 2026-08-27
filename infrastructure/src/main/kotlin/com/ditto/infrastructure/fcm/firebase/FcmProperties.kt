package com.ditto.infrastructure.fcm.firebase

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ditto.fcm")
data class FcmProperties(
    // Firebase 서비스 계정 키(.json)의 내용 전체. 파일이 아니라 값인 이유: ECS 가 Secrets Manager 의
    // 시크릿을 환경변수(FCM_CREDENTIALS)로만 주입한다 (task-definition.json 의 다른 시크릿과 같은 방식).
    val credentials: String,
)
