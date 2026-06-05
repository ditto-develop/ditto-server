package com.ditto.api.user

import com.ditto.api.support.RestDocsTest
import com.ditto.api.user.dto.CreateUserRequest
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.Member
import com.ditto.domain.socialaccount.entity.SocialAccount
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.domain.socialaccount.repository.SocialAccountRepository
import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document
import com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName
import com.epages.restdocs.apispec.ResourceDocumentation.resource
import com.epages.restdocs.apispec.ResourceSnippetParameters
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

class UserControllerTest : RestDocsTest() {

    @Autowired
    private lateinit var socialAccountRepository: SocialAccountRepository

    @Test
    @DisplayName("회원가입에 성공한다")
    fun register() {
        val member = memberRepository.save(Member(nickname = "임시닉네임"))
        val request = CreateUserRequest(
            name = "김철수",
            nickname = "철수123",
            phoneNumber = "010-1234-5678",
            gender = Gender.MALE,
            age = 25,
            interests = setOf("travel", "music"),
            location = "seoul",
            job = "it-tech",
        )

        mockMvc.perform(
            post("/api/v1/users")
                .withApiKey()
                .withBearerToken(member.id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").exists())
            .andExpect(jsonPath("$.data.nickname").value("철수123"))
            .andDo(
                document(
                    "user-register",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Users")
                            .summary("회원가입")
                            .description("소셜 로그인 후 추가 정보를 입력하여 회원가입을 완료합니다.")
                            .requestFields(
                                fieldWithPath("name").description("이름").optional(),
                                fieldWithPath("nickname").description("닉네임 (2~10자, 한글·영문·숫자)").optional(),
                                fieldWithPath("phoneNumber").description("전화번호 (010-0000-0000)").optional(),
                                fieldWithPath("email").description("이메일").optional(),
                                fieldWithPath("gender").description("성별 (MALE, FEMALE)").optional(),
                                fieldWithPath("age").description("나이대 (20, 25, 30, 35, 40, 45, 50, 60)").optional(),
                                fieldWithPath("birthDate").description("생년월일").optional(),
                                fieldWithPath("interests[]").description("관심사 code 목록 (필수, 최소 1개)"),
                                fieldWithPath("location").description("사는곳 code (필수)"),
                                fieldWithPath("job").description("직업 code (필수)"),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.id").description("사용자 ID"),
                                fieldWithPath("data.name").description("이름"),
                                fieldWithPath("data.nickname").description("닉네임"),
                                fieldWithPath("data.phoneNumber").description("전화번호"),
                                fieldWithPath("data.email").description("이메일"),
                                fieldWithPath("data.gender").description("성별"),
                                fieldWithPath("data.age").description("나이대"),
                                fieldWithPath("data.birthDate").description("생년월일"),
                                fieldWithPath("data.interests[]").description("관심사 code 목록"),
                                fieldWithPath("data.location").description("사는곳 code"),
                                fieldWithPath("data.job").description("직업 code"),
                                fieldWithPath("data.joinedAt").description("가입일시"),
                                fieldWithPath("data.role").description("역할"),
                                fieldWithPath("data.createdAt").description("생성일시"),
                                fieldWithPath("data.updatedAt").description("수정일시"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("가입 정보(email·생년월일)를 조회한다 - PENDING 회원도 접근 가능")
    fun getMe() {
        val member = memberRepository.save(
            Member(
                nickname = "가입정보유저",
                email = "user@kakao.com",
                birthDate = LocalDateTime.of(1995, 3, 15, 0, 0),
            ),
        )

        mockMvc.perform(
            get("/api/v1/users/me")
                .withApiKey()
                .withBearerToken(member.id),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.email").value("user@kakao.com"))
            .andExpect(jsonPath("$.data.birthDate").value("1995-03-15"))
            .andDo(
                document(
                    "user-me",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Users")
                            .summary("가입 정보 조회")
                            .description(
                                "소셜 로그인에서 받아온 가입 정보(이메일·생년월일)를 조회합니다. " +
                                    "온보딩 화면 prefill 용도이며, 가입 미완료(PENDING) 회원도 접근할 수 있습니다.",
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.email").description("이메일 (없으면 null)"),
                                fieldWithPath("data.birthDate").description("생년월일 (없거나 음력이면 null)"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("닉네임 사용 가능 여부를 확인한다")
    fun checkNicknameAvailability() {
        mockMvc.perform(
            get("/api/v1/users/nickname/{nickname}/availability", "사용가능닉네임")
                .withApiKey(),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.available").value(true))
            .andDo(
                document(
                    "nickname-availability",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Users")
                            .summary("닉네임 중복 확인")
                            .description("닉네임이 사용 가능한지 확인합니다.")
                            .pathParameters(
                                parameterWithName("nickname").description("확인할 닉네임"),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.available").description("닉네임 사용 가능 여부"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("회원 탈퇴에 성공한다")
    fun leaveUser() {
        val member = memberRepository.save(Member(nickname = "탈퇴유저").apply { activate() })
        socialAccountRepository.save(SocialAccount.create(member.id, SocialProvider.KAKAO, "test-user"))

        mockMvc.perform(
            post("/api/v1/users/{id}/leave", member.id)
                .withApiKey()
                .withBearerToken(member.id),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").exists())
            .andDo(
                document(
                    "user-leave",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Users")
                            .summary("회원 탈퇴")
                            .description("회원을 탈퇴 처리합니다.")
                            .pathParameters(
                                parameterWithName("id").description("사용자 ID"),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.id").description("사용자 ID"),
                                fieldWithPath("data.name").description("이름"),
                                fieldWithPath("data.nickname").description("닉네임"),
                                fieldWithPath("data.phoneNumber").description("전화번호"),
                                fieldWithPath("data.email").description("이메일"),
                                fieldWithPath("data.gender").description("성별"),
                                fieldWithPath("data.age").description("나이대"),
                                fieldWithPath("data.birthDate").description("생년월일"),
                                fieldWithPath("data.joinedAt").description("가입일시"),
                                fieldWithPath("data.role").description("역할"),
                                fieldWithPath("data.createdAt").description("생성일시"),
                                fieldWithPath("data.updatedAt").description("수정일시"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }
}
