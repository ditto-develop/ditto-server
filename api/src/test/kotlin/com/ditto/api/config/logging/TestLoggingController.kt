package com.ditto.api.config.logging

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
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

    @Loggable
    @GetMapping("/api/test/logging/warn")
    fun throwWarn(): Unit = throw WarnException(ErrorCode.FORBIDDEN)

    @Loggable
    @GetMapping("/api/test/logging/boom")
    fun throwUnexpected(): Unit = throw IllegalStateException("예기치 않은 오류")
}

/** 클래스 레벨 `@Loggable` — 핸들러마다 붙이지 않아도 진입점이 되는지 검증한다. */
@Loggable
@RestController
class TestClassLoggableController {

    @GetMapping("/api/test/logging/class-level")
    fun classLevel(@RequestParam name: String): String = name
}

data class MaskedRequest(val email: String, @Mask val password: String)

data class MaskedResponse(val email: String, @Mask val token: String)

@Service
class TestLoggingService {
    fun process(value: String): String = "processed-$value"
}
