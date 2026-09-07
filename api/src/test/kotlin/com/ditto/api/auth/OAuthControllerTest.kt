package com.ditto.api.auth

import com.ditto.api.auth.dto.AppleNativeLoginRequest
import com.ditto.api.auth.dto.NativeSocialLoginRequest
import com.ditto.api.support.RestDocsTest
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document
import com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName
import com.epages.restdocs.apispec.ResourceDocumentation.resource
import com.epages.restdocs.apispec.ResourceSnippetParameters
import io.kotest.matchers.shouldBe
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

class OAuthControllerTest : RestDocsTest() {

    companion object {
        private val PROVIDER_DESCRIPTION =
            "소셜 로그인 제공자 (${SocialProvider.entries.joinToString(", ") { it.name }})"
    }

    @Test
    @DisplayName("소셜 로그인 페이지로 리다이렉트한다")
    fun login() {
        mockMvc.perform(get("/api/v1/users/social-login/{provider}", "KAKAO").withApiKey())
            .andExpect(status().isFound)
            .andExpect(header().exists("Location"))
            .andDo(
                document(
                    "oauth-login",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("OAuth")
                            .summary("소셜 로그인")
                            .description("소셜 로그인 제공자의 인가 페이지로 리다이렉트합니다.")
                            .pathParameters(
                                parameterWithName("provider").description(PROVIDER_DESCRIPTION),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("콜백은 accessToken·signupRequired를 쿼리로, refreshToken을 HttpOnly 쿠키로 전달한다")
    fun callback() {
        mockMvc.perform(
            get("/api/v1/users/social-login/{provider}/callback", "KAKAO").withApiKey()
                .param("code", "test-auth-code"),
        )
            .andExpect(status().isFound)
            .andExpect(header().string("Location", startsWith("http://localhost:3000/auth/callback")))
            .andExpect(header().string("Location", containsString("accessToken")))
            .andExpect(header().string("Location", containsString("signupRequired")))
            .andExpect(header().string("Set-Cookie", containsString("refreshToken=")))
            .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
            .andDo(
                document(
                    "oauth-callback",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("OAuth")
                            .summary("소셜 로그인 콜백")
                            .description(
                                "소셜 로그인 인가 코드를 받아 토큰을 발급한 뒤 프론트 콜백 페이지로 리다이렉트한다. " +
                                    "accessToken과 회원가입 필요 여부(signupRequired)는 쿼리 파라미터로, " +
                                    "refreshToken은 HttpOnly 쿠키로 전달된다.",
                            )
                            .pathParameters(
                                parameterWithName("provider").description(PROVIDER_DESCRIPTION),
                            )
                            .queryParameters(
                                parameterWithName("code").description("소셜 로그인 제공자로부터 받은 인가 코드"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("네이티브 로그인은 카카오 액세스 토큰을 우리 토큰으로 교환한다")
    fun nativeLogin() {
        val request = NativeSocialLoginRequest(accessToken = "kakao-sdk-access-token")

        mockMvc.perform(
            post("/api/v1/users/social-login/kakao/native")
                .withApiKey()
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.data.signupRequired").value(true))
            .andExpect(jsonPath("$.data.sanctioned").value(false))
            .andExpect(header().string("Set-Cookie", containsString("refreshToken=")))
            .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
            .andDo(
                document(
                    "oauth-native-login",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("OAuth")
                            .summary("네이티브 소셜 로그인 (앱)")
                            .description(
                                "네이티브 카카오 SDK가 받아온 액세스 토큰을 우리 accessToken으로 교환한다(앱 전용). " +
                                    "웹은 리다이렉트 로그인을 그대로 쓰며 이 엔드포인트는 대체가 아니라 추가다. " +
                                    "refreshToken은 리다이렉트 로그인과 동일하게 HttpOnly 쿠키로 내려간다. " +
                                    "제재 회원은 accessToken 없이 sanctioned=true와 sanctionCode(MEMBER_SUSPENDED|MEMBER_BANNED), " +
                                    "정지면 suspendedUntil까지 함께 받는다.",
                            )
                            .requestFields(
                                fieldWithPath("accessToken").description("네이티브 소셜 SDK가 발급받은 액세스 토큰"),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.accessToken").type(JsonFieldType.STRING).optional()
                                    .description("우리 서비스 accessToken (제재 회원은 null)"),
                                fieldWithPath("data.signupRequired").description("회원가입(추가 정보 입력) 필요 여부"),
                                fieldWithPath("data.sanctioned").description("제재 회원 여부"),
                                fieldWithPath("data.sanctionCode").type(JsonFieldType.STRING).optional()
                                    .description("제재 코드 (MEMBER_SUSPENDED | MEMBER_BANNED, 제재가 아니면 null)"),
                                fieldWithPath("data.suspendedUntil").type(JsonFieldType.STRING).optional()
                                    .description("정지 해제 예정 일시 (정지만 존재, 그 외 null)"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("네이티브 로그인도 제재 회원에게는 토큰 없이 제재 사실만 알린다")
    fun nativeLoginWithSuspendedMember() {
        val request = NativeSocialLoginRequest(accessToken = "kakao-sdk-access-token")
        // 첫 로그인으로 회원을 만든 뒤 정지시킨다 — Fake 카카오 클라이언트는 항상 같은 소셜 계정을 반환한다.
        mockMvc.perform(
            post("/api/v1/users/social-login/kakao/native")
                .withApiKey()
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        ).andExpect(status().isOk)

        val member = memberRepository.findAll().first()
        member.activate()
        member.suspendUntil(LocalDateTime.now().plusDays(7))
        memberRepository.save(member)

        mockMvc.perform(
            post("/api/v1/users/social-login/kakao/native")
                .withApiKey()
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.accessToken").doesNotExist())
            .andExpect(jsonPath("$.data.sanctioned").value(true))
            .andExpect(jsonPath("$.data.sanctionCode").value("MEMBER_SUSPENDED"))
            .andExpect(jsonPath("$.data.suspendedUntil").isNotEmpty)
            .andExpect(header().doesNotExist("Set-Cookie"))
    }

    @Test
    @DisplayName("네이티브 로그인에 API Key가 없으면 401을 반환한다")
    fun nativeLoginWithoutApiKey() {
        val request = NativeSocialLoginRequest(accessToken = "kakao-sdk-access-token")

        mockMvc.perform(
            post("/api/v1/users/social-login/kakao/native")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    @DisplayName("애플 네이티브 로그인은 ID 토큰을 우리 토큰으로 교환한다")
    fun appleNativeLogin() {
        val request = AppleNativeLoginRequest(
            identityToken = "apple-identity-token",
            rawNonce = "client-generated-nonce",
            name = "김철수",
        )

        mockMvc.perform(
            post("/api/v1/users/social-login/apple/native")
                .withApiKey()
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.data.signupRequired").value(true))
            .andExpect(jsonPath("$.data.sanctioned").value(false))
            .andExpect(header().string("Set-Cookie", containsString("refreshToken=")))
            .andDo(
                document(
                    "oauth-apple-native-login",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("OAuth")
                            .summary("애플 네이티브 로그인 (앱)")
                            .description(
                                "애플 네이티브 SDK가 받아온 ID 토큰(JWT)을 우리 accessToken으로 교환한다(앱 전용). " +
                                    "서버가 애플 공개키로 서명과 iss·aud·exp를 검증하며, rawNonce를 함께 보내면 " +
                                    "토큰의 nonce까지 대조한다. 응답과 refreshToken 쿠키는 카카오 네이티브 로그인과 동일하다. " +
                                    "애플은 이름을 최초 인가 1회만 주므로 그때 앱이 name으로 함께 보낸다.",
                            )
                            .requestFields(
                                fieldWithPath("identityToken").description("애플 SDK가 발급한 ID 토큰(JWT)"),
                                fieldWithPath("rawNonce")
                                    .description("앱이 만든 원본 nonce (선택, 권장). 애플에는 SHA-256 해시를 넘긴다")
                                    .optional(),
                                fieldWithPath("name")
                                    .description("사용자 이름 (선택). 애플이 최초 인가 1회만 주므로 그때만 채워 보낸다")
                                    .optional(),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.accessToken").type(JsonFieldType.STRING).optional()
                                    .description("우리 서비스 accessToken (제재 회원은 null)"),
                                fieldWithPath("data.signupRequired").description("회원가입(추가 정보 입력) 필요 여부"),
                                fieldWithPath("data.sanctioned").description("제재 회원 여부"),
                                fieldWithPath("data.sanctionCode").type(JsonFieldType.STRING).optional()
                                    .description("제재 코드 (MEMBER_SUSPENDED | MEMBER_BANNED, 제재가 아니면 null)"),
                                fieldWithPath("data.suspendedUntil").type(JsonFieldType.STRING).optional()
                                    .description("정지 해제 예정 일시 (정지만 존재, 그 외 null)"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("애플 로그인은 카카오와 다른 회원으로 이어진다 (계정 연결 없음)")
    fun appleAndKakaoAreSeparateMembers() {
        mockMvc.perform(
            post("/api/v1/users/social-login/kakao/native")
                .withApiKey()
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(NativeSocialLoginRequest("kakao-token"))),
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/api/v1/users/social-login/apple/native")
                .withApiKey()
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(AppleNativeLoginRequest("apple-identity-token"))),
        ).andExpect(status().isOk)

        // 이메일이 같더라도 잇지 않는다 — 애플 릴레이 주소와 계정 탈취 위험 때문(ADR 0022).
        memberRepository.count() shouldBe 2
    }

    @Test
    @DisplayName("애플 네이티브 로그인에 identityToken이 없으면 거부한다")
    fun appleNativeLoginWithoutToken() {
        mockMvc.perform(
            post("/api/v1/users/social-login/apple/native")
                .withApiKey()
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"identityToken":""}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("0001"))
    }

    @Test
    @DisplayName("지원하지 않는 provider로 로그인 요청 시 에러를 반환한다")
    fun loginWithUnsupportedProvider() {
        mockMvc.perform(get("/api/v1/users/social-login/{provider}", "google").withApiKey())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("1001"))
    }
}
