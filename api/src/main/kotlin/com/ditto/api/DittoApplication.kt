package com.ditto.api

import com.ditto.domain.config.DomainConfig
import com.ditto.infrastructure.fcm.config.FcmConfig
import com.ditto.infrastructure.oauth.config.OAuthConfig
import com.ditto.infrastructure.storage.config.StorageConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import
import java.util.TimeZone

@SpringBootApplication(scanBasePackages = ["com.ditto.api"])
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
