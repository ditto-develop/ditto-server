package com.ditto.api.userreport.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive

/**
 * 신고 이미지 업로드 URL 발급 요청.
 *
 * 파일당 크기·타입이 presigned 서명에 포함되므로 클라이언트는 요청한 값 그대로만 업로드할 수 있다.
 */
data class IssueImageUploadUrlsRequest(
    @field:Valid
    @field:NotEmpty(message = "발급할 파일 정보가 없습니다.")
    val files: List<ImageUploadFileRequest> = emptyList(),
)

data class ImageUploadFileRequest(
    @field:Pattern(regexp = "image/.+", message = "이미지 파일만 첨부할 수 있습니다.")
    val contentType: String = "",

    @field:Positive(message = "파일 크기는 0보다 커야 합니다.")
    @field:Max(value = MAX_IMAGE_BYTES, message = "파일 크기는 5MB 이하여야 합니다.")
    val contentLength: Long = 0,
) {
    companion object {
        const val MAX_IMAGE_BYTES = 5L * 1024 * 1024
    }
}

data class ImageUploadUrlsResponse(
    val uploads: List<ImageUploadUrlResponse>,
)

/**
 * [uploadUrl]로 파일을 PUT 업로드한 뒤, 신고 접수 시 [objectKey]를 전달한다.
 */
data class ImageUploadUrlResponse(
    val objectKey: String,
    val uploadUrl: String,
)
