package com.ditto.common.exception

enum class ErrorCode(
    val status: Int,
    val code: String,
    val message: String,
) {
    INVALID_INPUT(400, "COMMON_001", "\uc798\ubabb\ub41c \uc785\ub825\uac12\uc785\ub2c8\ub2e4"),
    RESOURCE_NOT_FOUND(404, "COMMON_002", "\ub9ac\uc18c\uc2a4\ub97c \ucc3e\uc744 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4"),
    INTERNAL_ERROR(500, "COMMON_999", "\uc11c\ubc84 \ub0b4\ubd80 \uc624\ub958\uac00 \ubc1c\uc0dd\ud588\uc2b5\ub2c8\ub2e4"),
}
