package com.ditto.api.feedback.dto

import com.ditto.api.support.image.ImageUploadFileRequest
import com.ditto.domain.feedback.entity.FeedbackImage
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

data class IssueFeedbackImageUploadUrlsRequest(
    @field:Valid
    @field:NotEmpty(message = "발급할 파일 정보가 없습니다.")
    @field:Size(max = FeedbackImage.MAX_COUNT, message = "이미지 첨부는 최대 3장까지 가능합니다.")
    val files: List<ImageUploadFileRequest>,
)
