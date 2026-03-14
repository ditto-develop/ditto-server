package com.ditto.common.response

import com.ditto.common.exception.ErrorCode

data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ErrorDetail? = null,
) {
    companion object {

        fun <T> ok(data: T): ApiResponse<T> = ApiResponse(success = true, data = data)

        fun error(errorCode: ErrorCode, message: String? = null): ApiResponse<Unit> = ApiResponse(
            success = false,
            error = ErrorDetail(
                statusCode = errorCode.status,
                code = errorCode.code,
                message = message ?: errorCode.message,
            ),
        )

        fun error(status: Int, code: String, message: String): ApiResponse<Unit> = ApiResponse(
            success = false,
            error = ErrorDetail(statusCode = status, code = code, message = message),
        )
    }
}

data class ErrorDetail(
    val statusCode: Int,
    val code: String,
    val message: String,
)
