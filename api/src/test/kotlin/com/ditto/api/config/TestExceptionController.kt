package com.ditto.api.config

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.common.exception.WarnException
import com.ditto.common.logging.Loggable
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@Loggable
@RestController
class TestExceptionController {

    @GetMapping("/api/test/warn")
    fun throwWarn(): Unit = throw WarnException(ErrorCode.BAD_REQUEST, "잘못된 요청")

    @GetMapping("/api/test/error")
    fun throwError(): Unit = throw ErrorException(ErrorCode.INTERNAL_ERROR, "서버 오류")

    @GetMapping("/api/test/unhandled")
    fun throwUnhandled(): Unit = throw IllegalStateException("예기치 않은 오류")

    @PostMapping("/api/test/validation")
    fun throwValidation(@Valid @RequestBody request: TestRequest): Unit = Unit
}

data class TestRequest(
    @field:NotBlank
    val name: String?,
)
