package com.ditto.api

import com.ditto.domain.config.JpaConfig
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

@SpringBootApplication(scanBasePackages = ["com.ditto"])
@ConfigurationPropertiesScan(basePackages = ["com.ditto"])
@Import(JpaConfig::class)
class DittoApplication

fun main(args: Array<String>) {
    runApplication<DittoApplication>(*args)
}
