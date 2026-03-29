package com.ditto.api.config.exception

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.common.exception.WarnException
import com.ditto.common.response.ApiResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException


@ResponseStatus(HttpStatus.OK)
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(WarnException::class)
    fun handleWarnException(e: WarnException): ApiResponse<Unit> {
        logger.warn { "[${e.errorCode.code}] ${e.message}" }
        return ApiResponse.error(e.errorCode, e.message)
    }

    @ExceptionHandler(ErrorException::class)
    fun handleErrorException(e: ErrorException): ApiResponse<Unit> {
        logger.error(e) { "[${e.errorCode.code}] ${e.message}" }
        return ApiResponse.error(e.errorCode, e.message)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(e: MethodArgumentNotValidException): ApiResponse<Unit> {
        logger.warn(e) { "[VALIDATION] 잘못된 파라미터 요청." }
        return ApiResponse.error(ErrorCode.BAD_REQUEST)
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(e: MethodArgumentTypeMismatchException): ApiResponse<Unit> {
        logger.warn { "[TYPE_MISMATCH] ${e.name}: ${e.value}" }
        return ApiResponse.error(ErrorCode.UNSUPPORTED_PROVIDER)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMessageNotReadable(e: HttpMessageNotReadableException): ApiResponse<Unit> {
        logger.warn { "[PARSE_ERROR] ${e.message}" }
        return ApiResponse.error(ErrorCode.BAD_REQUEST)
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ApiResponse<Unit> {
        logger.error(e) { "[UNHANDLED] ${e.message}" }
        return ApiResponse.error(ErrorCode.INTERNAL_ERROR)
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
