package com.ditto.api.support

import io.kotest.core.spec.style.FreeSpec
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import javax.sql.DataSource

@SpringBootTest
@ActiveProfiles("local", "test")
abstract class IntegrationTest(
    private val dataSource: DataSource,
    body: IntegrationTest.() -> Unit = {},
) : FreeSpec() {
    override fun extensions() = listOf(DatabaseCleanExtension(dataSource))

    init {
        body()
    }
}
