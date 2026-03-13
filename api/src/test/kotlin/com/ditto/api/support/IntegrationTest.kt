package com.ditto.api.support

import io.kotest.core.spec.style.FreeSpec
import io.kotest.extensions.spring.SpringExtension
import org.springframework.boot.test.context.SpringBootTest
import javax.sql.DataSource

@SpringBootTest
abstract class IntegrationTest(
    private val dataSource: DataSource,
    body: IntegrationTest.() -> Unit = {},
) : FreeSpec() {
    override fun extensions() = listOf(SpringExtension, DatabaseCleanExtension(dataSource))
    init { body() }
}
