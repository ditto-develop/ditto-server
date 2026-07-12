package com.ditto.api.userreport.service

import com.ditto.api.userreport.dto.ImageUploadUrlResponse
import com.ditto.api.userreport.dto.ImageUploadUrlsResponse
import com.ditto.api.userreport.dto.IssueImageUploadUrlsRequest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.memberreport.entity.MemberReportImage
import com.ditto.infrastructure.storage.ObjectStorage
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class UserReportService(
    private val objectStorage: ObjectStorage,
) {

    fun issueImageUploadUrls(memberId: Long, request: IssueImageUploadUrlsRequest): ImageUploadUrlsResponse {
        if (request.files.size > MemberReportImage.MAX_COUNT) {
            throw WarnException(ErrorCode.REPORT_IMAGE_LIMIT_EXCEEDED)
        }
        val uploads = request.files.map { file ->
            val objectKey = "$PENDING_KEY_PREFIX/$memberId/${UUID.randomUUID()}"
            ImageUploadUrlResponse(
                objectKey = objectKey,
                uploadUrl = objectStorage.issueUploadUrl(objectKey, file.contentType, file.contentLength),
            )
        }
        return ImageUploadUrlsResponse(uploads = uploads)
    }

    companion object {
        // 접수되지 않은 업로드는 S3 라이프사이클 규칙이 pending/ 접두사 기준으로 삭제한다.
        const val PENDING_KEY_PREFIX = "pending/user-reports"
    }
}
