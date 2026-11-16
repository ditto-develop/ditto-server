package com.ditto.api.match

import com.ditto.api.match.controller.SecretMatchingStateController
import com.ditto.api.match.dto.SecretMatchingStateResponse
import com.ditto.api.match.service.SecretMatchingStateService
import com.ditto.api.support.ControllerUnitTest
import com.ditto.api.system.ServerTimeProvider
import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document
import com.epages.restdocs.apispec.ResourceDocumentation.resource
import com.epages.restdocs.apispec.ResourceSnippetParameters
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.time.LocalDateTime

class SecretMatchingStateControllerTest : ControllerUnitTest() {

    private val secretMatchingStateService: SecretMatchingStateService = mockk()
    private val serverTimeProvider: ServerTimeProvider = mockk {
        every { now() } returns LocalDateTime.of(2026, 11, 19, 10, 0)
    }

    override val controller = SecretMatchingStateController(secretMatchingStateService, serverTimeProvider)

    @Test
    @DisplayName("시크릿 매칭 홈 상태를 조회한다")
    fun getSecretMatchingState() {
        every { secretMatchingStateService.getSecretMatchingState(any(), any()) } returns SecretMatchingStateResponse(
            state = SecretMatchingState.ELIGIBLE,
            nextRoundAt = LocalDateTime.of(2026, 11, 20, 20, 0),
            quizSetId = 812L,
            weekStartedOn = LocalDate.of(2026, 11, 16),
        )

        mockMvc.perform(get("/api/v1/matching/secret-state"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.state").value("ELIGIBLE"))
            .andExpect(jsonPath("$.data.quizSetId").value(812))
            .andDo(
                document(
                    "secret-matching-state",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Matching")
                            .summary("시크릿 매칭 홈 상태 조회")
                            .description(
                                "홈에 띄울 시크릿 매칭 카드를 서버 시각 기준으로 정해 줍니다. 아래 순서로 먼저 맞는 것이 카드입니다.\n\n" +
                                    "- **NONE**: 카드 없음 (기능 꺼짐, 월~수, 이번 주 1:1 셋 없음, 이미 성사, 경고 제재 중, 금 00:00 전 그룹 완주자)\n" +
                                    "- **SECRET_RESULT**: 시크릿 매칭 후보가 있음. 일요일까지 결과 화면(SM-06)으로 보낸다\n" +
                                    "- **WAITING**: 늦게 완주해 nextRoundAt 라운드를 기다림 (SM-02)\n" +
                                    "- **AUTO_ENTERED**: 완주했고 아직 성사가 없어 nextRoundAt 라운드에 자동 참여 (SM-03)\n" +
                                    "- **ELIGIBLE**: 1:1 퀴즈를 풀면 시크릿 매칭에 참여 (SM-01). quizSetId 로 퀴즈에 바로 들어간다\n" +
                                    "- **ENDED**: 토 20:00 라운드까지 끝남 (SM-04). 목요일 정규 후보가 남아 있어도 ENDED\n\n" +
                                    "라운드 cron 은 실제 시각에만 돈다. 시각 오버라이드로 옮겼으면 어드민 [시크릿 매칭 지금 돌리기]를 눌러야 후보가 생긴다.",
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.state").description("카드 상태 (NONE / ELIGIBLE / WAITING / AUTO_ENTERED / SECRET_RESULT / ENDED)"),
                                fieldWithPath("data.nextRoundAt").description(
                                    "다음 시크릿 매칭 라운드 시각. \"금/토요일\" 문구를 고를 때 쓴다. NONE·ENDED 이거나 남은 라운드가 없으면 null",
                                ).optional(),
                                fieldWithPath("data.quizSetId").description("이번 주 1:1 퀴즈셋 ID. 기능이 꺼져 있거나 셋이 없으면 null").optional(),
                                fieldWithPath("data.weekStartedOn").description("판단 기준 운영 주 시작일(월요일)"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }
}
