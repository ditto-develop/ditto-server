package com.ditto.api.config

import com.ditto.api.support.RestDocsTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@Import(TestExceptionController::class)
class GlobalExceptionHandlerTest : RestDocsTest() {

    @Test
    @DisplayName("WarnException이 발생하면 success=false와 해당 ErrorCode 정보를 반환한다")
    fun warnException() {
        mockMvc.perform(get("/api/test/warn").withApiKey().withBearerToken())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.statusCode").value(400))
            .andExpect(jsonPath("$.error.code").value("0001"))
            .andExpect(jsonPath("$.error.message").value("잘못된 요청"))
    }

    @Test
    @DisplayName("ErrorException이 발생하면 success=false와 해당 ErrorCode 정보를 반환한다")
    fun errorException() {
        mockMvc.perform(get("/api/test/error").withApiKey().withBearerToken())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.statusCode").value(500))
            .andExpect(jsonPath("$.error.code").value("9999"))
            .andExpect(jsonPath("$.error.message").value("서버 오류"))
    }

    @Test
    @DisplayName("처리되지 않은 예외가 발생하면 success=false와 INTERNAL_ERROR를 반환한다")
    fun unhandledException() {
        mockMvc.perform(get("/api/test/unhandled").withApiKey().withBearerToken())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.statusCode").value(500))
            .andExpect(jsonPath("$.error.code").value("9999"))
    }

    @Test
    @DisplayName("유효하지 않은 요청 바디가 들어오면 success=false와 BAD_REQUEST를 반환한다")
    fun invalidRequestBody() {
        mockMvc.perform(
            post("/api/test/validation")
                .withApiKey()
                .withBearerToken()
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": ""}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.statusCode").value(400))
            .andExpect(jsonPath("$.error.code").value("0001"))
    }

    @Test
    @DisplayName("파싱할 수 없는 요청 바디가 들어오면 success=false와 BAD_REQUEST를 반환한다")
    fun malformedJson() {
        mockMvc.perform(
            post("/api/test/validation")
                .withApiKey()
                .withBearerToken()
                .contentType(MediaType.APPLICATION_JSON)
                .content("invalid json"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.statusCode").value(400))
            .andExpect(jsonPath("$.error.code").value("0001"))
    }

    @Test
    @DisplayName("필수 쿼리 파라미터가 빠지면 500이 아니라 BAD_REQUEST를 반환한다")
    fun missingRequiredParameter() {
        mockMvc.perform(get("/api/test/required-param").withApiKey().withBearerToken())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.statusCode").value(400))
            .andExpect(jsonPath("$.error.code").value("0001"))
    }

    @Test
    @DisplayName("지원하지 않는 메서드로 호출하면 500이 아니라 NOT_FOUND를 반환한다")
    fun methodNotSupported() {
        mockMvc.perform(post("/api/test/warn").withApiKey().withBearerToken())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.statusCode").value(404))
            .andExpect(jsonPath("$.error.code").value("0004"))
    }
}
