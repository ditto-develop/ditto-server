package com.ditto.api.match

import com.ditto.api.match.controller.MatchAdminController
import com.ditto.api.match.service.MatchmakingService
import com.ditto.api.support.ControllerUnitTest
import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document
import com.epages.restdocs.apispec.ResourceDocumentation.resource
import com.epages.restdocs.apispec.ResourceSnippetParameters
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class MatchAdminControllerTest : ControllerUnitTest() {

    private val matchmakingService: MatchmakingService = mockk()

    override val controller = MatchAdminController(matchmakingService)

    @Test
    @DisplayName("특정 퀴즈셋의 매칭 후보를 재생성한다")
    fun regenerateMatching() {
        justRun { matchmakingService.generateMatchingCandidates(any()) }

        mockMvc.perform(post("/api/v1/admin/quiz-sets/{quizSetId}/matching/regenerate", 10L))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andDo(
                document(
                    "admin-match-regenerate",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    pathParameters(parameterWithName("quizSetId").description("퀴즈 세트 ID")),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Matching")
                            .summary("[Admin] 퀴즈셋 매칭 재생성")
                            .description(
                                "특정 퀴즈셋의 1:1 매칭 후보를 재생성합니다(기존 후보 삭제 후 재계산). " +
                                    "마감·후보 존재 여부와 무관하게 실행됩니다.",
                            )
                            .pathParameters(parameterWithName("quizSetId").description("퀴즈 세트 ID"))
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data").description("응답 데이터 (없음)").optional(),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)").optional(),
                            )
                            .build(),
                    ),
                ),
            )

        verify { matchmakingService.generateMatchingCandidates(10L) }
    }
}
