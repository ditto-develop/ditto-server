package com.ditto.api.userreport

import com.ditto.api.support.IntegrationTest
import com.ditto.api.userreport.dto.ImageUploadFileRequest
import com.ditto.api.userreport.dto.IssueImageUploadUrlsRequest
import com.ditto.api.userreport.service.UserReportService
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import javax.sql.DataSource

class UserReportServiceTest(
    private val userReportService: UserReportService,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "신고 이미지 업로드 URL 발급" - {

        "요청한 파일 수만큼 업로드 URL과 객체 키를 발급한다" {
            val request = IssueImageUploadUrlsRequest(
                files = listOf(
                    ImageUploadFileRequest(contentType = "image/png", contentLength = 1024L),
                    ImageUploadFileRequest(contentType = "image/jpeg", contentLength = 2048L),
                    ImageUploadFileRequest(contentType = "image/webp", contentLength = 4096L),
                ),
            )

            val result = userReportService.issueImageUploadUrls(memberId = 1L, request = request)

            result.uploads.size shouldBe 3
            result.uploads.forEach { upload ->
                upload.objectKey shouldStartWith "${UserReportService.PENDING_KEY_PREFIX}/1/"
                upload.uploadUrl.isNotBlank() shouldBe true
            }
        }

        "발급된 객체 키는 모두 서로 다르다" {
            val request = IssueImageUploadUrlsRequest(
                files = List(3) { ImageUploadFileRequest(contentType = "image/png", contentLength = 1024L) },
            )

            val result = userReportService.issueImageUploadUrls(memberId = 1L, request = request)

            result.uploads.map { it.objectKey }.toSet().size shouldBe 3
        }

        "최대 장수를 초과하면 발급을 거부한다" {
            val request = IssueImageUploadUrlsRequest(
                files = List(4) { ImageUploadFileRequest(contentType = "image/png", contentLength = 1024L) },
            )

            val exception = shouldThrow<WarnException> {
                userReportService.issueImageUploadUrls(memberId = 1L, request = request)
            }

            exception.errorCode shouldBe ErrorCode.REPORT_IMAGE_LIMIT_EXCEEDED
        }
    }
})
