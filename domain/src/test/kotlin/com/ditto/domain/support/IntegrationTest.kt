package com.ditto.domain.support

import com.ditto.domain.config.DomainConfig
import io.kotest.core.spec.style.FreeSpec
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import javax.sql.DataSource

@SpringBootTest(classes = [DomainConfig::class])
@EnableAutoConfiguration
@ActiveProfiles("test")
abstract class IntegrationTest(
    private val dataSource: DataSource,
    body: IntegrationTest.() -> Unit = {},
) : FreeSpec() {
    override fun extensions() = listOf(DatabaseCleanExtension(dataSource))

    init {
        body()
    }
}
