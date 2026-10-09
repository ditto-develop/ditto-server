package com.ditto.api.auth

import com.ditto.api.auth.dto.AppleServerNotificationRequest
import com.ditto.api.support.RestDocsTest
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.socialaccount.entity.SocialAccount
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.domain.socialaccount.repository.SocialAccountRepository
import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document
import com.epages.restdocs.apispec.ResourceDocumentation.resource
import com.epages.restdocs.apispec.ResourceSnippetParameters
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// 애플 서버가 직접 부르는 경로라 API Key 를 싣지 않고 호출한다.
class AppleServerNotificationControllerTest : RestDocsTest() {

    @Autowired
    lateinit var socialAccountRepository: SocialAccountRepository

    @Test
    @DisplayName("애플 계정 삭제 알림을 받으면 그 회원을 탈퇴시킨다")
    fun receiveAccountDeleted() {
        val member = memberRepository.save(Member(nickname = "애플회원").apply { activate() })
        socialAccountRepository.save(
            SocialAccount.create(memberId = member.id, provider = SocialProvider.APPLE, providerUserId = APPLE_SUBJECT),
        )
        // test 프로필의 페이크 검증기는 "이벤트타입:sub" 를 읽는다. 실제로는 애플이 서명한 JWS 다.
        val request = AppleServerNotificationRequest(payload = "account-delete:$APPLE_SUBJECT")

        mockMvc.perform(
            post("/api/v1/users/social-login/apple/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andDo(
                document(
                    "apple-server-notification",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("OAuth")
                            .summary("애플 서버 간 알림 수신")
                            .description(
                                "애플이 계정 상태 변경을 알려 주는 엔드포인트다. FE가 호출하는 API가 아니며 API Key 없이 열려 있다. " +
                                    "payload 는 애플이 서명한 JWS 이고 서버가 애플 공개키로 검증한다. " +
                                    "account-delete 와 consent-revoked 는 탈퇴로 처리하고, 진행 중인 매칭·채팅이 있으면 " +
                                    "세션만 끊고 탈퇴를 미룬다(이미 받은 access token 은 만료까지 유효). " +
                                    "email-disabled·email-enabled 는 기록만 한다. " +
                                    "local·test 프로필은 서명 없이 `이벤트타입:sub` 문자열을 받는다(예시 값). " +
                                    "로컬 페이크 애플 로그인의 sub 는 `001234.fake-apple-subject.0000` 이다.",
                            )
                            .requestFields(
                                fieldWithPath("payload").description("애플이 서명한 JWS"),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data").description("응답 데이터 (없음)").optional(),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )

        memberRepository.findById(member.id).orElseThrow().status shouldBe MemberStatus.LEFT
    }

    @Test
    @DisplayName("검증할 수 없는 알림은 거절한다")
    fun rejectInvalidNotification() {
        val request = AppleServerNotificationRequest(payload = "not-a-notification")

        mockMvc.perform(
            post("/api/v1/users/social-login/apple/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("1005"))
    }

    companion object {
        private const val APPLE_SUBJECT = "001234.apple-notification.0000"
    }
}
