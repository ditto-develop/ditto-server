package com.ditto.api.feedback

import com.ditto.api.feedback.dto.CreateFeedbackRequest
import com.ditto.api.feedback.dto.IssueFeedbackImageUploadUrlsRequest
import com.ditto.api.feedback.service.FeedbackService
import com.ditto.api.support.RestDocsTest
import com.ditto.api.support.image.ImageUploadFileRequest
import com.ditto.domain.feedback.entity.FeedbackType
import com.ditto.domain.member.entity.Member
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

// FE와 공유하는 code 목록. enum에서 만든다.
private val FEEDBACK_TYPE_CODES = FeedbackType.entries.joinToString(", ") { "${it.code}(${it.description})" }

class FeedbackControllerTest : RestDocsTest() {

    @Autowired
    private lateinit var feedbackService: FeedbackService

    @Test
    @DisplayName("의견 이미지 업로드 URL을 발급한다")
    fun issueImageUploadUrls() {
        val request = IssueFeedbackImageUploadUrlsRequest(
            files = listOf(
                ImageUploadFileRequest(contentType = "image/png", contentLength = 1024L),
                ImageUploadFileRequest(contentType = "image/jpeg", contentLength = 2048L),
            ),
        )

        mockMvc.perform(
            post("/api/v1/feedbacks/image-upload-urls")
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
                    "feedback-image-upload-urls",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Feedbacks")
                            .summary("의견 이미지 업로드 URL 발급")
                            .description(
                                "의견에 첨부할 스크린샷의 업로드 URL(presigned PUT)을 발급합니다. " +
                                    "최대 3장, 파일당 5MB 이하. 발급받은 uploadUrl로 파일을 PUT 업로드한 뒤 " +
                                    "의견 접수 때 objectKey를 imageKeys로 보냅니다. URL은 발급 후 10분간 유효합니다. " +
                                    "PUT 요청의 Content-Type은 발급 때 보낸 contentType과 같아야 하고, 본문 크기는 " +
                                    "contentLength와 정확히 같아야 합니다. 다르면 S3가 403을 돌려줍니다. " +
                                    "3장을 넘기거나 이미지가 아니거나 5MB를 넘으면 error.code 0001로 실패합니다.",
                            )
                            .requestFields(
                                fieldWithPath("files[].contentType").description("파일 MIME 타입 (image/* 만 허용)"),
                                fieldWithPath("files[].contentLength")
                                    .description("파일 크기 (바이트, 최대 5MB). 업로드할 파일 크기와 정확히 같아야 한다"),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.uploads[].objectKey").description("업로드 대상 객체 키 (의견 접수 때 전달)"),
                                fieldWithPath("data.uploads[].uploadUrl").description("presigned PUT 업로드 URL"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("업로드 URL은 3장을 넘겨 요청하면 발급하지 않는다")
    fun issueImageUploadUrlsOverLimit() {
        val request = IssueFeedbackImageUploadUrlsRequest(
            files = List(4) { ImageUploadFileRequest(contentType = "image/png", contentLength = 1024L) },
        )

        mockMvc.perform(
            post("/api/v1/feedbacks/image-upload-urls")
                .withApiKey()
                .withBearerToken()
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("0001"))
    }

    @Test
    @DisplayName("의견을 접수한다")
    fun createFeedback() {
        val member = memberRepository.save(Member(nickname = "제보자").apply { activate() })
        val imageKeys = feedbackService.issueImageUploadUrls(
            memberId = member.id,
            request = IssueFeedbackImageUploadUrlsRequest(
                files = listOf(ImageUploadFileRequest(contentType = "image/png", contentLength = 1024L)),
            ),
        ).uploads.map { it.objectKey }
        val request = CreateFeedbackRequest(
            type = "bug",
            content = "채팅방에서 사진을 보내면 회색 화면만 보여요.",
            appVersion = "1.4.0",
            os = "iOS 18.2",
            deviceModel = "iPhone15,2",
            imageKeys = imageKeys,
        )

        mockMvc.perform(
            post("/api/v1/feedbacks")
                .withApiKey()
                .withBearerToken(member.id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andDo(
                document(
                    "feedback-create",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Feedbacks")
                            .summary("의견 접수")
                            .description(
                                "앱에서 버그 제보·개선 제안을 보냅니다. 운영자가 확인만 하고 답변은 하지 않습니다. " +
                                    "앱 버전·OS·기기 모델은 버그 재현에 쓰이니 가능하면 항상 보내고, 앱에서 얻을 수 없을 때만 생략합니다. " +
                                    "스크린샷은 업로드 URL 발급 API로 업로드를 마친 objectKey를 imageKeys로 보냅니다. " +
                                    "내용이 공백뿐이거나 길이 제한을 넘거나 없는 type이거나 이미지가 3장을 넘으면 error.code 0001로 실패합니다. " +
                                    "같은 키를 두 번 보냈거나, 발급받지 않은 키·다른 회원의 키·신고용 키이거나, " +
                                    "PUT 업로드가 끝나지 않았거나, 이미 접수에 쓴 키면 6005로 실패합니다. " +
                                    "6005가 나면 업로드 URL을 다시 받아 올린 뒤 재접수합니다. " +
                                    "응답을 못 받고 재시도한 경우의 6005는 앞 요청이 이미 접수됐을 수 있습니다.",
                            )
                            .requestFields(
                                fieldWithPath("type").description("의견 유형 code. 가능한 값: $FEEDBACK_TYPE_CODES"),
                                fieldWithPath("content").description("내용 (최대 1000자)"),
                                fieldWithPath("appVersion").description("앱 버전 (선택, 최대 30자)").optional(),
                                fieldWithPath("os")
                                    .description("'<플랫폼> <버전>' 형식의 OS (선택, 최대 30자). 예: iOS 18.2, Android 15")
                                    .optional(),
                                fieldWithPath("deviceModel")
                                    .description("OS가 주는 기기 모델 식별자 (선택, 최대 50자). 예: iPhone15,2, SM-S928N")
                                    .optional(),
                                fieldWithPath("imageKeys")
                                    .description("첨부 스크린샷 objectKey 목록 (선택, 최대 3개). 첨부가 없으면 생략하거나 []로 보낸다 (null 불가)")
                                    .optional(),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.id").description("접수된 의견 ID"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("내용이 공백뿐이면 접수하지 않는다")
    fun createFeedbackWithBlankContent() {
        val request = CreateFeedbackRequest(type = "bug", content = " ")

        mockMvc.perform(
            post("/api/v1/feedbacks")
                .withApiKey()
                .withBearerToken()
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("0001"))
    }

    @Test
    @DisplayName("이미지를 3장 넘게 보내면 0001로 실패한다")
    fun createFeedbackWithTooManyImages() {
        val request = CreateFeedbackRequest(
            type = "bug",
            content = "사진이 안 보여요.",
            imageKeys = List(4) { "pending/feedbacks/1/key-$it" },
        )

        mockMvc.perform(
            post("/api/v1/feedbacks")
                .withApiKey()
                .withBearerToken()
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("0001"))
    }

    @Test
    @DisplayName("없는 유형 code면 들어온 값을 메시지에 담아 실패한다")
    fun createFeedbackWithUnknownType() {
        val request = CreateFeedbackRequest(type = "BUG", content = "사진이 안 보여요.")

        mockMvc.perform(
            post("/api/v1/feedbacks")
                .withApiKey()
                .withBearerToken()
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("0001"))
            .andExpect(jsonPath("$.error.message").value("지원하지 않는 의견 유형입니다: BUG"))
    }
}
