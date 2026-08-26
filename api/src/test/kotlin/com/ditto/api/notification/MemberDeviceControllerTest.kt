package com.ditto.api.notification

import com.ditto.api.notification.dto.MemberDeviceRegisterRequest
import com.ditto.api.support.RestDocsTest
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.notification.MemberDeviceFixture
import com.ditto.domain.notification.entity.DevicePlatform
import com.ditto.domain.notification.repository.MemberDeviceRepository
import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document
import com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName
import com.epages.restdocs.apispec.ResourceDocumentation.resource
import com.epages.restdocs.apispec.ResourceSnippetParameters
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// 실제 FCM 등록 토큰 모양(163자, 콜론 포함) — DELETE 경로 변수 매핑이 콜론에서 깨지지 않는지도 함께 태운다.
private const val REAL_SHAPE_TOKEN = "dGVzdC1pbnN0YW5jZS1pZA:APA91bFxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"

class MemberDeviceControllerTest : RestDocsTest() {

    @Autowired
    private lateinit var memberDeviceRepository: MemberDeviceRepository

    @Test
    @DisplayName("디바이스 토큰을 등록한다")
    fun registerDevice() {
        val request = MemberDeviceRegisterRequest(token = REAL_SHAPE_TOKEN, platform = DevicePlatform.IOS)

        mockMvc.perform(
            post("/api/v1/notifications/devices")
                .withApiKey()
                .withBearerToken()
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.registered").value(true))
            .andDo(
                document(
                    "notification-devices-register",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Notification")
                            .summary("푸시 디바이스 토큰 등록")
                            .description(
                                "앱이 FCM 에서 받은 디바이스 토큰을 등록합니다. 로그인 직후·앱 실행·토큰 갱신 때마다 호출하세요. " +
                                    "재호출해도 행이 늘지 않으며(멱등), 다른 회원의 토큰이었다면 소유자를 요청 회원으로 갱신합니다. " +
                                    "registered 는 이번 호출로 이 회원 소유가 됐는지입니다(신규·소유권 이전 true, " +
                                    "이미 내 토큰이던 재호출 false) — false 도 실패가 아닙니다. 실패는 success 가 말합니다.",
                            )
                            .requestFields(
                                fieldWithPath("token").description("FCM 디바이스 토큰 (최대 512자)"),
                                fieldWithPath("platform").description("기기 플랫폼 (IOS, ANDROID)"),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.registered")
                                    .description("이번 호출로 이 회원 소유가 됐는지. 멱등 재호출이면 false (실패 아님)"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("같은 토큰을 다시 등록하면 registered 가 false 다")
    fun registerDeviceAgain() {
        val member = saveMember("재등록회원")
        memberDeviceRepository.save(MemberDeviceFixture.create(memberId = member.id, token = REAL_SHAPE_TOKEN))
        val request = MemberDeviceRegisterRequest(token = REAL_SHAPE_TOKEN, platform = DevicePlatform.ANDROID)

        mockMvc.perform(
            post("/api/v1/notifications/devices")
                .withApiKey()
                .withBearerToken(member.id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.registered").value(false))
    }

    @Test
    @DisplayName("디바이스 토큰을 해제한다")
    fun unregisterDevice() {
        val member = saveMember("해제회원")
        memberDeviceRepository.save(MemberDeviceFixture.create(memberId = member.id, token = REAL_SHAPE_TOKEN))

        mockMvc.perform(
            delete("/api/v1/notifications/devices/{token}", REAL_SHAPE_TOKEN)
                .withApiKey()
                .withBearerToken(member.id),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andDo(
                document(
                    "notification-devices-unregister",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Notification")
                            .summary("푸시 디바이스 토큰 해제")
                            .description(
                                "로그아웃·탈퇴 직전에 호출하세요. 이미 없는 토큰이어도 성공합니다(멱등). " +
                                    "다른 회원의 토큰이면 404 로 응답합니다.",
                            )
                            .pathParameters(parameterWithName("token").description("해제할 FCM 디바이스 토큰"))
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data").description("응답 본문 없음").optional(),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("남의 토큰은 해제할 수 없다")
    fun unregisterOthersTokenRejected() {
        val owner = saveMember("토큰주인")
        memberDeviceRepository.save(MemberDeviceFixture.create(memberId = owner.id, token = REAL_SHAPE_TOKEN))

        mockMvc.perform(
            delete("/api/v1/notifications/devices/{token}", REAL_SHAPE_TOKEN)
                .withApiKey()
                .withBearerToken(),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("8301"))
    }

    private fun saveMember(nickname: String): Member = memberRepository.save(
        MemberFixture.create(
            nickname = nickname,
            email = "$nickname@example.com",
            status = MemberStatus.ACTIVE,
        ),
    )
}
