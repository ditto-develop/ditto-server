package com.ditto.api.setting.dto

import jakarta.validation.constraints.Positive

data class CreateBlockRequest(
    @field:Positive(message = "차단할 회원 ID가 올바르지 않습니다.")
    val memberId: Long,
)
