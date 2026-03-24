package com.ditto.domain.support

import io.kotest.core.spec.style.FreeSpec
import io.kotest.extensions.spring.SpringExtension
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import javax.sql.DataSource

@SpringBootTest(classes = [ApplicationConfig::class])
@ActiveProfiles("test")
abstract class IntegrationTest(
    private val dataSource: DataSource,
    body: IntegrationTest.() -> Unit = {},
) : FreeSpec() {
    override fun extensions() = listOf(SpringExtension, DatabaseCleanExtension(dataSource))

    init {
        body()
    }
}
