package com.ditto.infrastructure.support

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration

@SpringBootApplication(
    scanBasePackages = ["com.ditto.infrastructure"],
    exclude = [DataSourceAutoConfiguration::class, HibernateJpaAutoConfiguration::class],
)
open class ApplicationConfig
