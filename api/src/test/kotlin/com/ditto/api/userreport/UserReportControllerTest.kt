package com.ditto.api.userreport

import com.ditto.api.support.RestDocsTest
import com.ditto.api.userreport.dto.CreateUserReportRequest
import com.ditto.api.userreport.dto.ImageUploadFileRequest
import com.ditto.api.userreport.dto.IssueImageUploadUrlsRequest
import com.ditto.api.userreport.service.UserReportService
import com.ditto.domain.member.entity.Member
import com.ditto.domain.memberreport.entity.MemberReportReason
import com.ditto.domain.memberreport.entity.MemberReportSource
import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document
import com.epages.restdocs.apispec.ResourceDocumentation.resource
import com.epages.restdocs.apispec.ResourceSnippetParameters
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// FE와 공유하는 code 목록. enum에서 생성해 문서가 항상 최신 상태를 유지한다.
private val REPORT_REASON_CODES = MemberReportReason.entries.joinToString(", ") { "${it.code}(${it.description})" }
private val REPORT_SOURCE_CODES = MemberReportSource.entries.joinToString(", ") { "${it.code}(${it.description})" }

class UserReportControllerTest : RestDocsTest() {

    @Autowired
    private lateinit var userReportService: UserReportService

    @Test
    @DisplayName("신고 이미지 업로드 URL을 발급한다")
    fun issueImageUploadUrls() {
        val request = IssueImageUploadUrlsRequest(
            files = listOf(
                ImageUploadFileRequest(contentType = "image/png", contentLength = 1024L),
                ImageUploadFileRequest(contentType = "image/jpeg", contentLength = 2048L),
            ),
        )

        mockMvc.perform(
            post("/api/v1/user-reports/image-upload-urls")
                .withApiKey()
                .withBearerToken()
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.uploads.length()").value(2))
            .andDo(
                document(
                    "user-report-image-upload-urls",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("UserReports")
                            .summary("신고 이미지 업로드 URL 발급")
                            .description(
                                "신고에 첨부할 이미지의 업로드 URL(presigned PUT)을 발급합니다. " +
                                    "최대 3장, 파일당 5MB 이하. 발급받은 uploadUrl로 파일을 PUT 업로드한 뒤 " +
                                    "신고 접수 시 objectKey를 전달합니다. URL은 발급 후 10분간 유효합니다.",
                            )
                            .requestFields(
                                fieldWithPath("files[].contentType").description("파일 MIME 타입 (image/* 만 허용)"),
                                fieldWithPath("files[].contentLength").description("파일 크기 (바이트, 최대 5MB)"),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.uploads[].objectKey").description("업로드 대상 객체 키 (신고 접수 시 전달)"),
                                fieldWithPath("data.uploads[].uploadUrl").description("presigned PUT 업로드 URL"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("신고를 접수한다")
    fun createUserReport() {
        val reporter = memberRepository.save(Member(nickname = "신고자").apply { activate() })
        val reported = memberRepository.save(Member(nickname = "피신고자").apply { activate() })
        val imageKeys = userReportService.issueImageUploadUrls(
            memberId = reporter.id,
            request = IssueImageUploadUrlsRequest(
                files = listOf(ImageUploadFileRequest(contentType = "image/png", contentLength = 1024L)),
            ),
        ).uploads.map { it.objectKey }

        val request = CreateUserReportRequest(
            reportedMemberId = reported.id,
            reason = "inappropriate-behavior",
            source = "profile",
            detail = "대화 중 폭언을 반복했습니다.",
            imageKeys = imageKeys,
        )

        mockMvc.perform(
            post("/api/v1/user-reports")
                .withApiKey()
                .withBearerToken(reporter.id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andDo(
                document(
                    "user-report-create",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("UserReports")
                            .summary("신고 접수")
                            .description(
                                "상대 회원을 신고합니다. 자기 신고·검토 대기 중인 동일 대상 재신고는 거부되며, " +
                                    "회원당 하루 5건까지 접수할 수 있습니다. " +
                                    "이미지는 업로드 URL 발급 API로 업로드를 마친 objectKey를 전달합니다.",
                            )
                            .requestFields(
                                fieldWithPath("reportedMemberId").description("피신고자 회원 ID"),
                                fieldWithPath("reason").description("신고 사유 code. 가능한 값: $REPORT_REASON_CODES"),
                                fieldWithPath("source").description("신고 접수 위치 code. 가능한 값: $REPORT_SOURCE_CODES"),
                                fieldWithPath("detail").description("상세 설명 (선택, 최대 500자 — etc 사유는 필수)").optional(),
                                fieldWithPath("imageKeys").description("첨부 이미지 objectKey 목록 (선택, 최대 3개)").optional(),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.id").description("접수된 신고 ID"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }
}
