package com.ditto.api.config

import com.ditto.api.support.RestDocsTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@Import(TestExceptionController::class)
class SecurityConfigTest : RestDocsTest() {

    @Nested
    @DisplayName("헬스체크 엔드포인트")
    inner class HealthCheck {

        @Test
        @DisplayName("API Key 없이 접근할 수 있다")
        fun accessWithoutApiKey() {
            mockMvc.perform(get("/health"))
                .andExpect(status().isOk)
        }
    }

    @Nested
    @DisplayName("Actuator 엔드포인트")
    inner class Actuator {

        @Test
        @DisplayName("API Key 없이 접근할 수 있다")
        fun accessWithoutApiKey() {
            mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk)
        }

        @Test
        @DisplayName("prometheus 메트릭에 접근할 수 있다")
        fun accessPrometheus() {
            mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk)
        }
    }

    @Nested
    @DisplayName("API 엔드포인트")
    inner class ApiEndpoints {

        @Test
        @DisplayName("유효한 API Key로 접근하면 성공한다")
        fun validApiKey() {
            mockMvc.perform(get("/api/test/warn").withApiKey())
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.statusCode").value(400))
        }

        @Test
        @DisplayName("API Key가 없으면 401을 반환한다")
        fun missingApiKey() {
            mockMvc.perform(get("/api/test/warn"))
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.statusCode").value(401))
        }

        @Test
        @DisplayName("잘못된 API Key를 보내면 401을 반환한다")
        fun invalidApiKey() {
            mockMvc.perform(
                get("/api/test/warn")
                    .header("X-API-Key", "wrong-key"),
            )
                .andExpect(status().isUnauthorized)
                .andExpect(jsonPath("$.error.statusCode").value(401))
        }
    }

    @Nested
    @DisplayName("Docs 엔드포인트")
    inner class DocsEndpoints {

        @Test
        @DisplayName("API Key 없이 Swagger UI에 접근할 수 있다")
        fun accessDocsWithoutKey() {
            mockMvc.perform(get("/docs"))
                .andExpect(status().isOk)
        }
    }

    @Nested
    @DisplayName("허용되지 않은 경로")
    inner class UnknownPaths {

        @Test
        @DisplayName("등록되지 않은 경로는 차단된다")
        fun blockedWithoutKey() {
            mockMvc.perform(get("/unknown/path"))
                .andExpect(status().isForbidden)
        }

        @Test
        @DisplayName("API Key가 있어도 등록되지 않은 경로는 차단된다")
        fun blockedWithKey() {
            mockMvc.perform(
                get("/unknown/path")
                    .header("X-API-Key", TEST_API_KEY),
            )
                .andExpect(status().isForbidden)
        }
    }
}
