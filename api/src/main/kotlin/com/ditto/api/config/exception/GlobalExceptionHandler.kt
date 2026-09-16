package com.ditto.api.config.exception

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.common.exception.WarnException
import com.ditto.common.response.ApiResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestCookieException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException


@ResponseStatus(HttpStatus.OK)
@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(WarnException::class)
    fun handleWarnException(e: WarnException, request: HttpServletRequest): ApiResponse<Unit> {
        logger.warn { "[${e.errorCode.code}] ${e.message} ${request.describe()}" }
        return ApiResponse.error(e.errorCode, e.message)
    }

    @ExceptionHandler(ErrorException::class)
    fun handleErrorException(e: ErrorException, request: HttpServletRequest): ApiResponse<Unit> {
        logger.error(e) { "[${e.errorCode.code}] ${e.message} ${request.describe()}" }
        return ApiResponse.error(e.errorCode, e.message)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(e: MethodArgumentNotValidException, request: HttpServletRequest): ApiResponse<Unit> {
        logger.warn(e) { "[VALIDATION] 잘못된 파라미터 요청. ${request.describe()}" }
        return ApiResponse.error(ErrorCode.BAD_REQUEST)
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(e: MethodArgumentTypeMismatchException, request: HttpServletRequest): ApiResponse<Unit> {
        logger.warn { "[TYPE_MISMATCH] ${e.name}: ${e.value} ${request.describe()}" }
        return ApiResponse.error(ErrorCode.UNSUPPORTED_PROVIDER)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMessageNotReadable(e: HttpMessageNotReadableException, request: HttpServletRequest): ApiResponse<Unit> {
        logger.warn { "[PARSE_ERROR] ${e.message} ${request.describe()}" }
        return ApiResponse.error(ErrorCode.BAD_REQUEST)
    }

    @ExceptionHandler(MissingRequestCookieException::class)
    fun handleMissingCookie(e: MissingRequestCookieException, request: HttpServletRequest): ApiResponse<Unit> {
        logger.warn { "[MISSING_COOKIE] ${e.cookieName} ${request.describe()}" }
        return ApiResponse.error(ErrorCode.BAD_REQUEST)
    }

    /**
     * 필수 쿼리 파라미터 누락. 소셜 로그인 콜백을 `code` 없이 열면(사용자 취소·콜백 새로고침) 여기로 온다 —
     * 클라이언트 잘못이라 400 이고, 스택을 남길 일이 아니다.
     */
    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParameter(e: MissingServletRequestParameterException, request: HttpServletRequest): ApiResponse<Unit> {
        logger.warn { "[MISSING_PARAM] ${e.parameterName} ${request.describe()}" }
        return ApiResponse.error(ErrorCode.BAD_REQUEST)
    }

    /**
     * 없는 경로·지원하지 않는 메서드. 대부분 외부 스캐너(`/actuator/env` 등)와 오타다.
     * [handleException] 에 맡기면 500(INTERNAL_ERROR)에 스택까지 남아 진짜 장애처럼 보인다.
     */
    @ExceptionHandler(NoResourceFoundException::class, HttpRequestMethodNotSupportedException::class)
    fun handleNoResource(e: Exception, request: HttpServletRequest): ApiResponse<Unit> {
        logger.warn { "[NO_RESOURCE] ${e.message} ${request.describe()}" }
        return ApiResponse.error(ErrorCode.NOT_FOUND)
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception, request: HttpServletRequest): ApiResponse<Unit> {
        logger.error(e) { "[UNHANDLED] ${e.message} ${request.describe()}" }
        return ApiResponse.error(ErrorCode.INTERNAL_ERROR)
    }

    /**
     * 예외 로그에 어느 요청이었는지를 붙인다.
     *
     * 이게 없으면 로그에 남는 건 "[0004] 존재하지 않는 리소스입니다." 한 줄뿐이라 어느 API 인지 알 수 없다.
     * `@Loggable` 이 붙은 컨트롤러는 진입 로그로 유추할 수 있었지만 그 밖은 추적 자체가 불가능했다.
     * 회원 식별자는 MDC(`memberId`)에도 실리지만, 인증 전 단계의 예외는 MDC 가 비어 있어 여기서 다시 적는다.
     */
    private fun HttpServletRequest.describe(): String {
        val query = queryString?.let { "?$it" } ?: ""
        return "| $method $requestURI$query"
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}
