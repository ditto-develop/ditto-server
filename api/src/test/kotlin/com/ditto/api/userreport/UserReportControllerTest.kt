package com.ditto.api.userreport

import com.ditto.api.support.RestDocsTest
import com.ditto.api.userreport.dto.ImageUploadFileRequest
import com.ditto.api.userreport.dto.IssueImageUploadUrlsRequest
import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document
import com.epages.restdocs.apispec.ResourceDocumentation.resource
import com.epages.restdocs.apispec.ResourceSnippetParameters
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class UserReportControllerTest : RestDocsTest() {

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
}
