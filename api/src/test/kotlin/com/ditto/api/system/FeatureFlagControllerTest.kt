package com.ditto.api.system

import com.ditto.api.support.RestDocsTest
import com.ditto.api.system.featureflag.FeatureFlags
import com.ditto.domain.system.entity.FeatureFlag
import com.ditto.domain.system.entity.FeatureFlagKey
import com.ditto.domain.system.repository.FeatureFlagRepository
import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document
import com.epages.restdocs.apispec.ResourceDocumentation.resource
import com.epages.restdocs.apispec.ResourceSnippetParameters
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

class FeatureFlagControllerTest : RestDocsTest() {

    @Autowired
    lateinit var featureFlags: FeatureFlags

    @Autowired
    lateinit var featureFlagRepository: FeatureFlagRepository

    // 캐시는 빈 하나라 테스트 사이에 남는다. 앞 테스트가 읽어 둔 값을 버린다.
    @BeforeEach
    fun evictBefore() = featureFlags.evict()

    @AfterEach
    fun evictAfter() = featureFlags.evict()

    @Test
    @DisplayName("JWT 없이 API Key 만으로 요청한 플래그의 켜짐 여부를 조회한다 — 행이 없는 플래그와 모르는 키는 false")
    fun getFeatureFlags() {
        featureFlagRepository.save(
            FeatureFlag.of(FeatureFlagKey.RECORDS).apply { enable(LocalDateTime.of(2026, 11, 5, 18, 0), 1L) },
        )

        mockMvc.perform(
            get("/api/v1/feature-flags")
                .param("keys", "RECORDS")
                .param("keys", "QUIZ_CHAT")
                .param("keys", "NOT_YET_DEFINED")
                .withApiKey(),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.flags.RECORDS").value(true))
            .andExpect(jsonPath("$.data.flags.QUIZ_CHAT").value(false))
            .andExpect(jsonPath("$.data.flags.NOT_YET_DEFINED").value(false))
            .andExpect(jsonPath("$.data.flags.length()").value(3))
            .andDo(
                document(
                    "feature-flags",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("System")
                            .summary("서버 기능 플래그 조회")
                            .description(
                                "어드민(시스템 → 기능 플래그)에서 켜고 끄는 서버 기능 플래그 중 요청한 키의 켜짐 여부를 조회합니다. " +
                                    "X-API-Key 만 필요하고 JWT 는 필요 없습니다(로그인 전에도 호출). " +
                                    "응답에는 요청한 키만 들어 있습니다. 서버에 정의되지 않은 키와 한 번도 켠 적 없는 키는 false 입니다(오류 아님). " +
                                    "keys 가 없거나 비었거나, 20개를 넘거나, 50자를 넘거나 [A-Z0-9_] 밖의 글자가 있으면 400(0001) 입니다. " +
                                    "서버는 인스턴스마다 1분 캐시해 읽으므로 어드민에서 바꾼 값이 최대 1분 늦게 보일 수 있습니다.",
                            )
                            .queryParameters(
                                parameterWithName("keys").description("조회할 플래그 키. 반복(keys=A&keys=B) 또는 쉼표(keys=A,B). 앞뒤 공백·중복은 무시. 최대 20개"),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.flags").description("요청한 플래그 키 → 켜짐 여부"),
                                fieldWithPath("data.flags.RECORDS").description("예: 기록 탭·대화방 '우리의 답변' 화면 (FE)"),
                                fieldWithPath("data.flags.QUIZ_CHAT").description("예: 대화방 첫 대화 힌트 메시지와 질문 카드 전송 (IMP-1)"),
                                fieldWithPath("data.flags.NOT_YET_DEFINED").description("예: 서버에 없는 키 — false"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)").optional(),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("쉼표로 묶어 보내도 되고, 공백과 중복은 지우며, JWT 를 함께 보내도 같다")
    fun commaSeparatedKeysWithJwt() {
        mockMvc.perform(
            get("/api/v1/feature-flags")
                .param("keys", " QUIZ_CHAT ,RECORDS,QUIZ_CHAT")
                .withApiKey()
                .withBearerToken(),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.flags.length()").value(2))
            .andExpect(jsonPath("$.data.flags.QUIZ_CHAT").value(false))
            .andExpect(jsonPath("$.data.flags.RECORDS").value(false))
    }

    @Test
    @DisplayName("keys 가 없거나 비어 있으면 0001 이다")
    fun missingKeys() {
        mockMvc.perform(get("/api/v1/feature-flags").withApiKey())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("0001"))
        mockMvc.perform(get("/api/v1/feature-flags").param("keys", " , ").withApiKey())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("0001"))
    }

    @Test
    @DisplayName("키 모양이 enum 이름이 아니거나 너무 많으면 0001 이다")
    fun invalidKeys() {
        listOf("records", "QUIZ-CHAT", "A".repeat(51)).forEach { key ->
            mockMvc.perform(get("/api/v1/feature-flags").param("keys", key).withApiKey())
                .andExpect(jsonPath("$.error.code").value("0001"))
        }
        val tooMany = (1..21).joinToString(",") { "K$it" }
        mockMvc.perform(get("/api/v1/feature-flags").param("keys", tooMany).withApiKey())
            .andExpect(jsonPath("$.error.code").value("0001"))
    }

    @Test
    @DisplayName("API Key 가 없으면 401 이다")
    fun getFeatureFlagsWithoutApiKey() {
        mockMvc.perform(get("/api/v1/feature-flags").param("keys", "RECORDS"))
            .andExpect(status().isUnauthorized)
    }
}
