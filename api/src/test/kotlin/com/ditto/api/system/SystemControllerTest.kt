package com.ditto.api.system

import com.ditto.api.support.RestDocsTest
import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document
import com.epages.restdocs.apispec.ResourceDocumentation.resource
import com.epages.restdocs.apispec.ResourceSnippetParameters
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class SystemControllerTest : RestDocsTest() {

    @Test
    @DisplayName("현재 시스템 상태(연/월/주차/기간)를 조회한다")
    fun getSystemState() {
        mockMvc.perform(
            get("/api/v1/system/state")
                .withApiKey()
                .withBearerToken(),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.weekStartedOn").isString)
            .andExpect(jsonPath("$.data.year").isNumber)
            .andExpect(jsonPath("$.data.month").isNumber)
            .andExpect(jsonPath("$.data.week").isNumber)
            .andExpect(jsonPath("$.data.period").isString)
            .andDo(
                document(
                    "system-state",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("System")
                            .summary("시스템 상태 조회")
                            .description("현재 서버 시각 기준의 연/월/주차/기간(QUIZ_PERIOD, MATCHING_PERIOD, CHATTING_PERIOD)을 조회합니다. 어드민 시각 오버라이드가 반영됩니다.")
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.weekStartedOn").description("운영 주 시작일 (해당 주 월요일, 주간 식별자)"),
                                fieldWithPath("data.year").description("년도 (weekStartedOn 파생 표시값, FE 전환 후 제거 예정)"),
                                fieldWithPath("data.month").description("월 (weekStartedOn 파생 표시값, FE 전환 후 제거 예정)"),
                                fieldWithPath("data.week").description("주차 (weekStartedOn 파생 표시값, FE 전환 후 제거 예정)"),
                                fieldWithPath("data.period").description("기간 상태 (QUIZ_PERIOD, MATCHING_PERIOD, CHATTING_PERIOD)"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)").optional(),
                            )
                            .build(),
                    ),
                ),
            )
    }
}
