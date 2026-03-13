package com.ditto.common.exception

open class WarnException(
    val errorCode: ErrorCode,
    override val message: String = errorCode.message,
) : RuntimeException(message)

open class ErrorException(
    val errorCode: ErrorCode,
    override val message: String = errorCode.message,
    override val cause: Throwable? = null,
) : RuntimeException(message, cause)
