package com.ditto.api

import com.ditto.domain.config.DomainConfig
import com.ditto.infrastructure.oauth.config.OAuthConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import
import java.util.TimeZone

@SpringBootApplication(scanBasePackages = ["com.ditto.api"])
@ConfigurationPropertiesScan(basePackages = ["com.ditto.api"])
@Import(
    DomainConfig::class,
    OAuthConfig::class,
)
class DittoApplication

fun main(args: Array<String>) {
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))
    runApplication<DittoApplication>(*args)
}
