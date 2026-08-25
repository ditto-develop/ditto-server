package com.ditto.api.user

import com.ditto.api.support.RestDocsTest
import com.ditto.api.user.dto.LeaveRequest
import com.ditto.api.user.dto.CreateUserRequest
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.Interest
import com.ditto.domain.member.entity.Job
import com.ditto.domain.intronote.entity.IntroNote
import com.ditto.domain.intronote.entity.IntroQuestion
import com.ditto.domain.intronote.repository.IntroNoteRepository
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.member.entity.Location
import com.ditto.domain.member.entity.Member
import com.ditto.domain.socialaccount.entity.SocialAccount
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.domain.socialaccount.repository.SocialAccountRepository
import com.epages.restdocs.apispec.MockMvcRestDocumentationWrapper.document
import com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName
import com.epages.restdocs.apispec.ResourceDocumentation.resource
import com.epages.restdocs.apispec.ResourceSnippetParameters
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

// FE와 공유하는 code 목록. enum에서 생성해 문서가 항상 최신 상태를 유지한다.
private val INTEREST_CODES = Interest.entries.joinToString(", ") { it.code }
private val LOCATION_CODES = Location.entries.joinToString(", ") { it.code }
private val JOB_CODES = Job.entries.joinToString(", ") { it.code }

class UserControllerTest : RestDocsTest() {

    @Autowired
    private lateinit var socialAccountRepository: SocialAccountRepository

    @Autowired
    private lateinit var personalMatchRepository: PersonalMatchRepository

    @Autowired
    private lateinit var introNoteRepository: IntroNoteRepository

    @Test
    @DisplayName("introduction 을 포함해 가입하면 소개노트에 저장된다")
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
            caricature = "/onboarding/profileimg/avatar/m1.svg",
            introduction = "주말마다 한강 산책하는 걸 좋아해요!",
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
            .andExpect(jsonPath("$.data.caricature").value("/onboarding/profileimg/avatar/m1.svg"))
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
                                fieldWithPath("interests[]")
                                    .description("관심사 code 목록 (필수, 최소 1개). 가능한 값: $INTEREST_CODES"),
                                fieldWithPath("location").description("사는곳 code (필수). 가능한 값: $LOCATION_CODES"),
                                fieldWithPath("job").description("직업 code (필수). 가능한 값: $JOB_CODES"),
                                fieldWithPath("caricature")
                                    .description(
                                        "프로필 캐리커쳐 (필수, FE 문자열 그대로 저장). 아바타 경로를 실으면 " +
                                            "프로필 조회의 profileImageUrl 로 그대로 나갑니다",
                                    ),
                                fieldWithPath("introduction")
                                    .description(
                                        "한 줄 소개 (최대 50자). 소개노트 '나를 한 줄로 표현한다면?' 답변으로 저장됩니다. " +
                                            "이 응답에는 실리지 않으며 마이프로필 조회(GET /users/me/profile)의 introduction 으로 확인합니다",
                                    )
                                    .optional(),
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
                                fieldWithPath("data.caricature").description("프로필 캐리커쳐"),
                                fieldWithPath("data.joinedAt").description("가입일시"),
                                fieldWithPath("data.role").type(JsonFieldType.STRING)
                                    .description("역할 (현재 미지원, null)").optional(),
                                fieldWithPath("data.createdAt").description("생성일시"),
                                fieldWithPath("data.updatedAt").description("수정일시"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )

        // 한 줄 소개는 소개노트 ONE_WORD 에 저장된다 — 프로필 조회·소개노트 화면이 같은 값을 본다
        val introNote = introNoteRepository.findByMemberIdAndQuestion(member.id, IntroQuestion.ONE_WORD)
        introNote?.answer shouldBe "주말마다 한강 산책하는 걸 좋아해요!"
    }

    @Test
    @DisplayName("introduction 없이 가입하면 소개노트를 만들지 않는다")
    fun registerWithoutOptionalProfileFields() {
        val member = memberRepository.save(Member(nickname = "임시닉네임2"))
        val request = CreateUserRequest(
            nickname = "영희456",
            interests = setOf("travel"),
            location = "seoul",
            job = "it-tech",
            caricature = "m2",
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
            .andExpect(jsonPath("$.data.caricature").value("m2"))

        introNoteRepository.findByMemberIdAndQuestion(member.id, IntroQuestion.ONE_WORD) shouldBe null
    }

    @Test
    @DisplayName("가입 정보(카카오 수집 정보)를 조회한다 - PENDING 회원도 접근 가능")
    fun getMe() {
        val member = memberRepository.save(
            Member(
                nickname = "가입정보유저",
                email = "user@kakao.com",
                birthDate = LocalDateTime.of(1995, 3, 15, 0, 0),
                name = "홍길동",
                phoneNumber = "010-1234-5678",
                gender = Gender.MALE,
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
            .andExpect(jsonPath("$.data.name").value("홍길동"))
            .andExpect(jsonPath("$.data.phoneNumber").value("010-1234-5678"))
            .andExpect(jsonPath("$.data.gender").value("MALE"))
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
                                "소셜 로그인에서 받아온 가입 정보(이메일·생년월일·이름·전화번호·성별)를 조회합니다. " +
                                    "온보딩 화면 prefill 용도이며, 가입 미완료(PENDING) 회원도 접근할 수 있습니다.",
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.email").description("이메일 (없으면 null)"),
                                fieldWithPath("data.birthDate").description("생년월일 (없거나 음력이면 null)"),
                                fieldWithPath("data.name").description("이름 (미동의 시 null)"),
                                fieldWithPath("data.phoneNumber").description("전화번호 010-XXXX-XXXX (미동의 시 null)"),
                                fieldWithPath("data.gender").description("성별 MALE/FEMALE (미동의 시 null)"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("매칭된 상대의 공개 프로필을 조회한다")
    fun getPublicProfile() {
        val viewer = memberRepository.save(Member(nickname = "조회자").apply { activate() })
        val target = memberRepository.save(
            Member(
                nickname = "디토러버",
                gender = Gender.FEMALE,
                age = 27,
                interests = setOf(Interest.WORKOUT, Interest.TRAVEL, Interest.MUSIC),
                location = Location.SEOUL,
                job = Job.DESIGN,
                caricature = "/assets/avatar/f3.png",
            ).apply { activate() },
        )
        introNoteRepository.save(IntroNote.create(target.id, IntroQuestion.ONE_WORD, "안녕하세요, 만나서 반가워요!"))
        personalMatchRepository.save(
            PersonalMatchFixture.create(
                requesterId = viewer.id,
                receiverId = target.id,
                status = PersonalMatchStatus.ACCEPTED,
            ),
        )

        mockMvc.perform(
            get("/api/v1/users/{id}/profile", target.id)
                .withApiKey()
                .withBearerToken(viewer.id),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.nickname").value("디토러버"))
            .andExpect(jsonPath("$.data.occupation").value("design"))
            .andDo(
                document(
                    "user-public-profile",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Users")
                            .summary("타인 공개 프로필 조회")
                            .description(
                                "매칭이 성사된 상대(또는 같은 그룹채팅 참여자)의 공개 프로필을 조회합니다. " +
                                    "권한이 없으면 403. 민감정보(이메일·전화번호·실명)는 포함하지 않습니다.",
                            )
                            .pathParameters(
                                parameterWithName("id").description("대상 사용자 ID"),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.userId").description("대상 사용자 ID"),
                                fieldWithPath("data.nickname").description("닉네임"),
                                fieldWithPath("data.gender").description("성별 (MALE, FEMALE)").optional(),
                                fieldWithPath("data.age").description("나이").optional(),
                                fieldWithPath("data.introduction")
                                    .description("자기소개 (소개노트 one-word 답변, 없으면 null)").optional(),
                                fieldWithPath("data.profileImageUrl").description("프로필 이미지 경로").optional(),
                                fieldWithPath("data.location").description("지역 code. 가능한 값: $LOCATION_CODES").optional(),
                                fieldWithPath("data.occupation").description("직업 code. 가능한 값: $JOB_CODES").optional(),
                                fieldWithPath("data.interests[]").description("관심사 code 목록. 가능한 값: $INTEREST_CODES").optional(),
                                // 항상 null 인 필드는 type 을 명시해야 스키마에 실린다.
                                fieldWithPath("data.rating").type(JsonFieldType.NUMBER)
                                    .description("평점 (현재 미지원, null)").optional(),
                                fieldWithPath("data.preferredMinAge").type(JsonFieldType.NUMBER)
                                    .description("선호 최소 나이 (현재 미지원, null)").optional(),
                                fieldWithPath("data.preferredMaxAge").type(JsonFieldType.NUMBER)
                                    .description("선호 최대 나이 (현재 미지원, null)").optional(),
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
                .withBearerToken(member.id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        LeaveRequest(reason = "other", reasonDetail = "원하는 매칭 상대를 만나기 어려웠어요."),
                    ),
                ),
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
                            .description(
                                "회원을 탈퇴 처리합니다(소프트 삭제). 계정은 즉시 사용 불가가 되지만 데이터는 남으며, " +
                                    "30일 이내 같은 소셜 계정으로 재로그인하면 복구됩니다. 30일이 지나면 배치가 완전 삭제합니다. " +
                                    "진행 중인 매칭이나 채팅이 있으면 거부합니다.",
                            )
                            .pathParameters(
                                parameterWithName("id").description("사용자 ID"),
                            )
                            .requestFields(
                                fieldWithPath("reason").description("탈퇴 사유 code (선택, 최대 50자)").optional(),
                                fieldWithPath("reasonDetail")
                                    .description("탈퇴 사유 자유 입력 (선택, 최대 100자). '기타' 선택 시의 서술 — 기타가 아니어도 받습니다")
                                    .optional(),
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
                                fieldWithPath("data.role").type(JsonFieldType.STRING)
                                    .description("역할 (현재 미지원, null)").optional(),
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
