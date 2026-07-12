package com.ditto.infrastructure.storage.s3

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ditto.storage.s3")
data class StorageProperties(
    val bucket: String,
    val region: String,
    // 업로드 presigned URL 유효 시간. 발급 후 이 시간 안에만 업로드 가능.
    val putUrlTtl: Duration = Duration.ofMinutes(10),
)
