package com.ditto.api.chat.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

data class ChatImageUploadUrlsRequest(
    @field:Valid
    @field:NotEmpty(message = "발급할 파일 정보가 없습니다.")
    @field:Size(max = 10, message = "한 번에 최대 10장까지 업로드 URL을 발급합니다.")
    val files: List<ChatImageUploadFileRequest>,
)
