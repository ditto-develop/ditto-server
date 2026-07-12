package com.ditto.api.userreport.controller

import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.api.userreport.dto.ImageUploadUrlsResponse
import com.ditto.api.userreport.dto.IssueImageUploadUrlsRequest
import com.ditto.api.userreport.service.UserReportService
import com.ditto.common.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class UserReportController(
    private val userReportService: UserReportService,
) {

    @PostMapping("/api/v1/user-reports/image-upload-urls")
    fun issueImageUploadUrls(
        @Valid @RequestBody request: IssueImageUploadUrlsRequest,
        @AuthenticationPrincipal principal: MemberPrincipal,
    ): ApiResponse<ImageUploadUrlsResponse> {
        val result = userReportService.issueImageUploadUrls(principal.memberId, request)
        return ApiResponse.ok(result)
    }
}
