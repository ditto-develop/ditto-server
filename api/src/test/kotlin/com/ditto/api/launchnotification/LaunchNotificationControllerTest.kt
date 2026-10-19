package com.ditto.api.launchnotification

import com.ditto.api.launchnotification.dto.LaunchNotificationSignupRequest
import com.ditto.api.launchnotification.service.LaunchNotificationRateLimiter
import com.ditto.api.support.RestDocsTest
import com.ditto.domain.launchnotification.entity.LaunchPlatform
import com.ditto.domain.launchnotification.repository.LaunchNotificationRequestRepository
import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document
import com.epages.restdocs.apispec.ResourceDocumentation.resource
import com.epages.restdocs.apispec.ResourceSnippetParameters
import io.kotest.matchers.nulls.shouldNotBeNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.concurrent.atomic.AtomicInteger

class LaunchNotificationControllerTest : RestDocsTest() {

    @Autowired
    private lateinit var launchNotificationRequestRepository: LaunchNotificationRequestRepository

    // 리미터는 컨텍스트와 함께 테스트 간에 공유된다. 테스트마다 다른 IP로 보내 한도가 섞이지 않게 한다.
    private fun nextIp(): String = "203.0.113.${ipSeq.incrementAndGet()}"

    private fun perform(request: LaunchNotificationSignupRequest, clientIp: String = nextIp()): ResultActions =
        mockMvc.perform(
            post("/api/v1/launch-notification-requests")
                .withApiKey()
                .header("X-Forwarded-For", clientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )

    private fun signup(email: String = "visitor@example.com", privacyConsent: Boolean? = true) =
        LaunchNotificationSignupRequest(
            email = email,
            platform = "ANDROID",
            privacyConsent = privacyConsent,
            consentVersion = "2026-10-19",
        )

    @Test
    @DisplayName("출시 알림을 신청한다")
    fun requestLaunchNotification() {
        perform(signup(email = " Visitor@Example.com "))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").doesNotExist())
            .andDo(
                document(
                    "launch-notification-request",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("LaunchNotification")
                            .summary("앱 출시 알림 신청")
                            .description(
                                "로그인하지 않은 방문자가 앱 출시 알림을 신청합니다. X-API-Key만 필요하고 JWT는 받지 않습니다. " +
                                    "이메일은 앞뒤 공백을 지우고 소문자로 저장합니다. " +
                                    "같은 이메일·플랫폼으로 다시 신청해도 성공으로 응답하고(등록 여부를 드러내지 않음), " +
                                    "새로 만들지 않고 동의 시각과 버전만 갱신합니다. " +
                                    "이메일 형식이 틀리거나 254자를 넘거나, privacyConsent가 true가 아니거나, 모르는 platform이면 " +
                                    "error.code 0001. 같은 IP에서 1분에 " + LaunchNotificationRateLimiter.LIMIT_PER_WINDOW +
                                    "회를 넘기면 error.code 0005입니다.",
                            )
                            .requestFields(
                                fieldWithPath("email").description("이메일 (최대 254자)"),
                                fieldWithPath("platform").description("플랫폼: " + LaunchPlatform.entries.joinToString { it.name }),
                                fieldWithPath("privacyConsent").description("개인정보 수집·이용 동의 (true여야 함)"),
                                fieldWithPath("consentVersion").description("FE가 보여 준 동의 문구 버전 (최대 30자, 동의 증빙)"),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data").description("데이터 (항상 null)"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )

        launchNotificationRequestRepository.findByEmailAndPlatform("visitor@example.com", LaunchPlatform.ANDROID)
            .shouldNotBeNull()
    }

    @Test
    @DisplayName("이미 신청한 이메일로 다시 신청해도 같은 성공 응답을 준다")
    fun requestAgain() {
        perform(signup()).andExpect(jsonPath("$.success").value(true))

        perform(signup())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").doesNotExist())
    }

    @Test
    @DisplayName("개인정보 동의가 true가 아니면 0001")
    fun withoutConsent() {
        perform(signup(privacyConsent = false))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("0001"))
    }

    @Test
    @DisplayName("이메일 형식이 틀리면 0001")
    fun invalidEmail() {
        perform(signup(email = "not-an-email"))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("0001"))
    }

    @Test
    @DisplayName("같은 IP에서 1분에 한도를 넘기면 0005")
    fun rateLimited() {
        val ip = nextIp()
        repeat(LaunchNotificationRateLimiter.LIMIT_PER_WINDOW) {
            perform(signup(email = "visitor$it@example.com"), ip).andExpect(jsonPath("$.success").value(true))
        }

        perform(signup(email = "one-more@example.com"), ip)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("0005"))
    }

    @Test
    @DisplayName("API Key가 없으면 401을 반환한다")
    fun withoutApiKey() {
        mockMvc.perform(
            post("/api/v1/launch-notification-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signup())),
        )
            .andExpect(status().isUnauthorized)
    }

    companion object {
        private val ipSeq = AtomicInteger()
    }
}
