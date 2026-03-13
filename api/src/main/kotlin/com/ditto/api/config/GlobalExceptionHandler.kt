package com.ditto.api.config

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.common.exception.WarnException
import com.ditto.common.response.ApiResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

private val logger = KotlinLogging.logger {}

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(WarnException::class)
    fun handleWarnException(e: WarnException): ResponseEntity<ApiResponse<Unit>> {
        logger.warn { "[${e.errorCode.code}] ${e.message}" }
        return ResponseEntity.status(e.errorCode.status).body(ApiResponse.error(e.errorCode, e.message))
    }

    @ExceptionHandler(ErrorException::class)
    fun handleErrorException(e: ErrorException): ResponseEntity<ApiResponse<Unit>> {
        logger.error(e) { "[${e.errorCode.code}] ${e.message}" }
        return ResponseEntity.status(e.errorCode.status).body(ApiResponse.error(e.errorCode, e.message))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Unit>> {
        val message = e.bindingResult.fieldErrors.joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        logger.warn { "[VALIDATION] $message" }
        return ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.INVALID_INPUT, message))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMessageNotReadable(e: HttpMessageNotReadableException): ResponseEntity<ApiResponse<Unit>> {
        logger.warn { "[PARSE_ERROR] ${e.message}" }
        return ResponseEntity.badRequest().body(ApiResponse.error(ErrorCode.INVALID_INPUT, "\uc694\uccad \ubcf8\ubb38\uc744 \uc77d\uc744 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4"))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ApiResponse<Unit>> {
        logger.error(e) { "[UNHANDLED] ${e.message}" }
        return ResponseEntity.internalServerError().body(ApiResponse.error(ErrorCode.INTERNAL_ERROR))
    }
}
