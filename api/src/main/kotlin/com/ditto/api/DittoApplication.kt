package com.ditto.api

import com.ditto.domain.config.JpaConfig
import com.ditto.infrastructure.oauth.config.OAuthConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

@SpringBootApplication(scanBasePackages = ["com.ditto.api"])
@ConfigurationPropertiesScan(basePackages = ["com.ditto.api"])
@Import(
    JpaConfig::class,
    OAuthConfig::class,
)
class DittoApplication

fun main(args: Array<String>) {
    runApplication<DittoApplication>(*args)
}
