package com.ditto.api.userreport.dto

/**
 * [uploadUrl]로 파일을 PUT 업로드한 뒤, 신고 접수 시 [objectKey]를 전달한다.
 */
data class ImageUploadUrlResponse(
    val objectKey: String,
    val uploadUrl: String,
)
