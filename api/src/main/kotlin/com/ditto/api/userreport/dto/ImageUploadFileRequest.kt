package com.ditto.api.userreport.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive

data class ImageUploadFileRequest(
    @field:Pattern(regexp = "image/.+", message = "이미지 파일만 첨부할 수 있습니다.")
    val contentType: String,

    @field:Positive(message = "파일 크기는 0보다 커야 합니다.")
    @field:Max(value = MAX_IMAGE_BYTES, message = "파일 크기는 5MB 이하여야 합니다.")
    val contentLength: Long,
) {
    companion object {
        const val MAX_IMAGE_BYTES = 5L * 1024 * 1024
    }
}
