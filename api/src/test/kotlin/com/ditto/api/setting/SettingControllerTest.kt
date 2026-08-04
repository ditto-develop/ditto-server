package com.ditto.api.setting

import com.ditto.api.setting.dto.CreateBlockRequest
import com.ditto.api.setting.dto.UpdateNotificationSettingsRequest
import com.ditto.api.support.RestDocsTest
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberBlock
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberBlockRepository
import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document
import com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName
import com.epages.restdocs.apispec.ResourceDocumentation.resource
import com.epages.restdocs.apispec.ResourceSnippetParameters
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class SettingControllerTest : RestDocsTest() {

    @Autowired
    private lateinit var memberBlockRepository: MemberBlockRepository

    @Test
    @DisplayName("알림 설정을 조회한다 — 저장한 적이 없으면 기본값을 준다")
    fun getNotificationSettings() {
        val member = saveMember("알림설정회원")

        mockMvc.perform(
            get("/api/v1/users/me/notification-settings")
                .withApiKey()
                .withBearerToken(member.id),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.matching").value(true))
            .andExpect(jsonPath("$.data.chat").value(true))
            .andExpect(jsonPath("$.data.marketing").value(false))
            .andDo(
                document(
                    "notification-settings-get",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Settings")
                            .summary("알림 설정 조회")
                            .description(
                                "설정 화면의 알림 토글 3종을 조회합니다. " +
                                    "한 번도 저장하지 않은 회원은 기본값(매칭·채팅 수신, 마케팅 미수신)을 받습니다.",
                            )
                            .responseFields(*settingsResponseFields())
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("토글 하나만 보내면 그 항목만 바뀐다")
    fun updateNotificationSettings() {
        val member = saveMember("알림수정회원")
        val request = UpdateNotificationSettingsRequest(chat = false)

        mockMvc.perform(
            patch("/api/v1/users/me/notification-settings")
                .withApiKey()
                .withBearerToken(member.id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.chat").value(false))
            // 보내지 않은 항목은 기존 값(기본값) 그대로다.
            .andExpect(jsonPath("$.data.matching").value(true))
            .andExpect(jsonPath("$.data.marketing").value(false))
            .andDo(
                document(
                    "notification-settings-update",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Settings")
                            .summary("알림 설정 수정")
                            .description("부분 패치입니다. 생략(null)한 항목은 변경하지 않습니다.")
                            .requestFields(
                                fieldWithPath("matching").description("매칭 알림 수신 여부. 생략 시 변경 없음").optional(),
                                fieldWithPath("chat").description("채팅 알림 수신 여부. 생략 시 변경 없음").optional(),
                                fieldWithPath("marketing").description("마케팅 정보 수신 여부. 생략 시 변경 없음").optional(),
                            )
                            .responseFields(*settingsResponseFields())
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("차단 목록을 최신순으로 조회한다")
    fun getMyBlocks() {
        val member = saveMember("차단한회원")
        val older = saveMember("먼저차단된회원")
        val newer = saveMember("나중차단된회원")
        memberBlockRepository.save(MemberBlock.create(member.id, older.id))
        memberBlockRepository.save(MemberBlock.create(member.id, newer.id))

        mockMvc.perform(
            get("/api/v1/users/me/blocks")
                .withApiKey()
                .withBearerToken(member.id),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(2))
            .andDo(
                document(
                    "blocks-get",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Settings")
                            .summary("차단 목록 조회")
                            .description(
                                "내가 차단한 회원 목록을 최신순으로 조회합니다. 차단 사유는 노출하지 않습니다. " +
                                    "id는 차단된 회원 ID이며, 해제 API의 경로 변수로 그대로 사용합니다.",
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data[].id").description("차단된 회원 ID"),
                                fieldWithPath("data[].nickname").description("차단된 회원 닉네임"),
                                fieldWithPath("data[].profileImageUrl").description("차단된 회원 캐리커쳐").optional(),
                                fieldWithPath("data[].blockedAt").description("차단 일시"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("회원을 차단한다 — 같은 상대를 다시 차단해도 성공한다")
    fun block() {
        val member = saveMember("차단요청회원")
        val target = saveMember("차단대상회원")
        val request = CreateBlockRequest(memberId = target.id)

        repeat(2) {
            mockMvc.perform(
                post("/api/v1/users/me/blocks")
                    .withApiKey()
                    .withBearerToken(member.id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
        }

        // 멱등 — 두 번 요청해도 행은 하나다.
        memberBlockRepository.findAllByBlockerIdOrderByCreatedAtDesc(member.id).size.let { count ->
            check(count == 1) { "차단 행이 $count 개 생성됐다" }
        }

        mockMvc.perform(
            post("/api/v1/users/me/blocks")
                .withApiKey()
                .withBearerToken(member.id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andDo(
                document(
                    "blocks-create",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Settings")
                            .summary("회원 차단")
                            .description(
                                "신고 없이 차단만 합니다. 이미 차단한 상대면 아무 일도 일어나지 않습니다(멱등). " +
                                    "신고와 함께 차단하려면 POST /api/v1/user-reports 의 block 필드를 사용하세요. " +
                                    "차단하면 상대는 내 프로필을 볼 수 없고, 서로 매칭 후보에서 제외됩니다.",
                            )
                            .requestFields(
                                fieldWithPath("memberId").description("차단할 회원 ID"),
                            )
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
    @DisplayName("자기 자신은 차단할 수 없다")
    fun blockSelfRejected() {
        val member = saveMember("자기차단회원")

        mockMvc.perform(
            post("/api/v1/users/me/blocks")
                .withApiKey()
                .withBearerToken(member.id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(CreateBlockRequest(memberId = member.id))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
    }

    @Test
    @DisplayName("차단을 해제한다")
    fun unblock() {
        val member = saveMember("해제요청회원")
        val target = saveMember("해제대상회원")
        memberBlockRepository.save(MemberBlock.create(member.id, target.id))

        mockMvc.perform(
            delete("/api/v1/users/me/blocks/{memberId}", target.id)
                .withApiKey()
                .withBearerToken(member.id),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andDo(
                document(
                    "blocks-delete",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Settings")
                            .summary("차단 해제")
                            .description("차단을 해제합니다. 차단하지 않은 상대를 해제해도 성공으로 응답합니다(멱등).")
                            .pathParameters(
                                parameterWithName("memberId").description("차단 해제할 회원 ID"),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data").description("응답 본문 없음").optional(),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )

        check(memberBlockRepository.findAllByBlockerIdOrderByCreatedAtDesc(member.id).isEmpty()) {
            "차단이 해제되지 않았다"
        }
    }

    private fun saveMember(nickname: String): Member = memberRepository.save(
        MemberFixture.create(
            nickname = nickname,
            email = "$nickname@example.com",
            status = MemberStatus.ACTIVE,
            caricature = "/assets/avatar/m1.png",
        ),
    )

    private fun settingsResponseFields() = arrayOf(
        fieldWithPath("success").description("성공 여부"),
        fieldWithPath("data.matching").description("매칭 알림 수신 여부"),
        fieldWithPath("data.chat").description("채팅 알림 수신 여부"),
        fieldWithPath("data.marketing").description("마케팅 정보 수신 여부"),
        fieldWithPath("error").description("에러 정보 (성공 시 null)"),
    )
}
