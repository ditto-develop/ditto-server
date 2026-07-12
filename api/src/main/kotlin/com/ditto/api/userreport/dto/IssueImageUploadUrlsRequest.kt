package com.ditto.api.userreport.dto

import com.ditto.domain.memberreport.entity.MemberReportImage
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

/**
 * 신고 이미지 업로드 URL 발급 요청.
 *
 * 파일당 크기·타입이 presigned 서명에 포함되므로 클라이언트는 요청한 값 그대로만 업로드할 수 있다.
 */
data class IssueImageUploadUrlsRequest(
    @field:Valid
    @field:NotEmpty(message = "발급할 파일 정보가 없습니다.")
    @field:Size(max = MemberReportImage.MAX_COUNT, message = "이미지 첨부는 최대 3장까지 가능합니다.")
    val files: List<ImageUploadFileRequest>,
)
