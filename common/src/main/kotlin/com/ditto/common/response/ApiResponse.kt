package com.ditto.common.response

import com.ditto.common.exception.ErrorCode

data class ApiResponse<T>(
    val status: Int,
    val data: T? = null,
    val error: ErrorDetail? = null,
) {
    companion object {
        fun <T> ok(data: T): ApiResponse<T> = ApiResponse(status = 200, data = data)
        fun error(errorCode: ErrorCode, message: String? = null): ApiResponse<Unit> = ApiResponse(
            status = errorCode.status,
            error = ErrorDetail(code = errorCode.code, message = message ?: errorCode.message),
        )
        fun error(status: Int, code: String, message: String): ApiResponse<Unit> = ApiResponse(
            status = status,
            error = ErrorDetail(code = code, message = message),
        )
    }
}

data class ErrorDetail(val code: String, val message: String)
