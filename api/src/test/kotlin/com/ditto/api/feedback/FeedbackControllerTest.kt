package com.ditto.api.feedback

import com.ditto.api.feedback.dto.CreateFeedbackRequest
import com.ditto.api.support.RestDocsTest
import com.ditto.domain.feedback.entity.FeedbackType
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

// FE와 공유하는 code 목록. enum에서 만든다.
private val FEEDBACK_TYPE_CODES = FeedbackType.entries.joinToString(", ") { "${it.code}(${it.description})" }

class FeedbackControllerTest : RestDocsTest() {

    @Test
    @DisplayName("의견을 접수한다")
    fun createFeedback() {
        val request = CreateFeedbackRequest(
            type = "bug",
            content = "채팅방에서 사진을 보내면 회색 화면만 보여요.",
            appVersion = "1.4.0",
            os = "iOS 18.2",
            deviceModel = "iPhone15,2",
        )

        mockMvc.perform(
            post("/api/v1/feedbacks")
                .withApiKey()
                .withBearerToken()
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
                                    "내용이 공백뿐이거나 길이 제한을 넘거나 없는 type이면 error.code 0001로 실패합니다.",
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
