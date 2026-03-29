package com.ditto.api.config.logging

import com.ditto.common.logging.Loggable
import com.ditto.common.logging.Mask
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class TestLoggingController(private val testLoggingService: TestLoggingService) {

    @Loggable
    @GetMapping("/api/test/logging/echo")
    fun echo(@RequestParam name: String): String = name

    @Loggable
    @PostMapping("/api/test/logging/masked-request")
    fun maskedRequest(@RequestBody request: MaskedRequest): String = "ok"

    @Loggable
    @GetMapping("/api/test/logging/masked-response")
    fun maskedResponse(): MaskedResponse = MaskedResponse("user@test.com", "secret-token")

    @Loggable
    @GetMapping("/api/test/logging/call-service")
    fun callService(): String = testLoggingService.process("input")

    @GetMapping("/api/test/logging/direct-service")
    fun directService(): String = testLoggingService.process("direct")
}

data class MaskedRequest(val email: String, @Mask val password: String)

data class MaskedResponse(val email: String, @Mask val token: String)

@Service
class TestLoggingService {
    fun process(value: String): String = "processed-$value"
}
