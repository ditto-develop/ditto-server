package com.ditto.infrastructure.support

import io.kotest.core.spec.style.FreeSpec
import io.kotest.extensions.spring.SpringExtension
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(classes = [ApplicationConfig::class])
@ActiveProfiles("test")
abstract class IntegrationTest(
    body: IntegrationTest.() -> Unit = {},
) : FreeSpec() {
    override fun extensions() = listOf(SpringExtension)

    init {
        body()
    }
}
