package com.ditto.api.chat.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

/**
 * 업로드할 이미지 1건의 정보. contentType·contentLength 는 presigned 서명에 포함되어
 * 클라이언트는 발급받은 값 그대로만 업로드할 수 있다.
 */
data class ChatImageUploadFileRequest(
    @field:NotBlank
    val contentType: String,
    @field:Positive
    val contentLength: Long,
)
