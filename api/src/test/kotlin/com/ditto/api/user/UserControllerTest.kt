package com.ditto.api.user

import com.ditto.api.support.RestDocsTest
import com.ditto.api.user.dto.LeaveRequest
import com.ditto.api.user.dto.CreateUserRequest
import com.ditto.api.user.dto.UpdatePersonalInfoRequest
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.Interest
import com.ditto.domain.member.entity.Job
import com.ditto.domain.intronote.entity.IntroNote
import com.ditto.domain.intronote.entity.IntroQuestion
import com.ditto.domain.intronote.repository.IntroNoteRepository
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.quiz.QuizAnswerFixture
import com.ditto.domain.quiz.QuizFixture
import com.ditto.domain.quiz.QuizProgressFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.repository.QuizAnswerRepository
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.review.MemberReviewFixture
import com.ditto.domain.review.ReviewAnswerFixture
import com.ditto.domain.review.entity.MeetingStatus
import com.ditto.domain.review.entity.ReviewAnswerContent
import com.ditto.domain.review.repository.MemberReviewRepository
import com.ditto.domain.review.repository.ReviewAnswerRepository
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
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch
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

    @Autowired
    private lateinit var memberReviewRepository: MemberReviewRepository

    @Autowired
    private lateinit var reviewAnswerRepository: ReviewAnswerRepository

    @Autowired
    private lateinit var quizSetRepository: QuizSetRepository

    @Autowired
    private lateinit var quizRepository: QuizRepository

    @Autowired
    private lateinit var quizProgressRepository: QuizProgressRepository

    @Autowired
    private lateinit var quizAnswerRepository: QuizAnswerRepository

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
                                fieldWithPath("gender").description("성별 (MALE, FEMALE). **필수** — 매칭의 입력값입니다"),
                                fieldWithPath("age")
                                    .description(
                                        "나이 (20~100). **필수** — 매칭의 입력값입니다. " +
                                            "나이대 구간의 중앙값을 보냅니다(20~24 → 22, 25~29 → 27 … 60 이상 → 60)",
                                    ),
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
            gender = Gender.FEMALE,
            age = 27,
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
        matchAccepted(viewer.id, target.id)
        // 평점은 공개 기준(3건)을 넘겨야 값이 실린다.
        saveReceivedAnswer(target.id, MeetingStatus.MET, rating = 5, comment = "대화가 편하고 좋았어요")
        saveReceivedAnswer(target.id, MeetingStatus.MET, rating = 4, comment = null)
        saveReceivedAnswer(target.id, MeetingStatus.MET, rating = 3, comment = null)

        mockMvc.perform(
            get("/api/v1/users/{id}/profile", target.id)
                .withApiKey()
                .withBearerToken(viewer.id),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.nickname").value("디토러버"))
            .andExpect(jsonPath("$.data.occupation").value("design"))
            .andExpect(jsonPath("$.data.rating").value(4.0))
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
                                fieldWithPath("data.rating")
                                    .description("받은 평가 평균 (공개 기준 3건 미만이면 null)").optional(),
                                // 항상 null 인 필드는 type 을 명시해야 스키마에 실린다.
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
    @DisplayName("매칭된 상대가 받은 평가를 조회한다")
    fun getUserRatings() {
        val viewer = memberRepository.save(Member(nickname = "조회자R").apply { activate() })
        val target = memberRepository.save(Member(nickname = "평가받은사람").apply { activate() })
        matchAccepted(viewer.id, target.id)
        saveReceivedAnswer(target.id, MeetingStatus.MET, rating = 5, comment = "대화가 편하고 좋았어요")
        saveReceivedAnswer(target.id, MeetingStatus.MET, rating = 4, comment = "약속 시간 잘 지켜요")
        saveReceivedAnswer(target.id, MeetingStatus.NO_SHOW, rating = 3, comment = null)

        mockMvc.perform(
            get("/api/v1/users/{id}/ratings", target.id)
                .withApiKey()
                .withBearerToken(viewer.id),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.totalCount").value(3))
            .andExpect(jsonPath("$.data.averageScore").value(4.0))
            .andExpect(jsonPath("$.data.publicThreshold").value(3))
            .andExpect(jsonPath("$.data.noShowCount").value(1))
            .andDo(
                document(
                    "user-ratings",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Users")
                            .summary("타인 받은 평가 조회")
                            .description(
                                "매칭된 상대가 받은 평가 요약입니다. `GET /users/me/ratings`와 같은 스키마·같은 공개 기준으로, " +
                                    "총 평가가 publicThreshold(3)건 미만이면 totalCount만 실제 값이고 평균·노쇼는 0, 코멘트는 빈 배열입니다. " +
                                    "권한은 공개 프로필과 동일합니다(매칭된 상대 또는 같은 그룹채팅 참여자, 차단 관계면 403).",
                            )
                            .pathParameters(
                                parameterWithName("id").description("대상 사용자 ID"),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.averageScore").description("평균 별점 (비공개 시 0)"),
                                fieldWithPath("data.totalCount").description("받은 평가 총 건수"),
                                fieldWithPath("data.publicThreshold").description("공개 기준 건수 (3)"),
                                fieldWithPath("data.noShowCount").description("노쇼 평가를 받은 횟수 (비공개 시 0)"),
                                fieldWithPath("data.ratings").description("평가 목록 최신순 (비공개 시 빈 배열)"),
                                fieldWithPath("data.ratings[].comment").description("한줄 코멘트 (미입력이면 null)").optional(),
                                fieldWithPath("data.ratings[].createdAt").description("평가 확정 일시"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("매칭되지 않은 상대의 평가는 조회할 수 없다")
    fun getUserRatingsWithoutMatch() {
        val viewer = memberRepository.save(Member(nickname = "남남1").apply { activate() })
        val target = memberRepository.save(Member(nickname = "남남2").apply { activate() })

        mockMvc.perform(
            get("/api/v1/users/{id}/ratings", target.id)
                .withApiKey()
                .withBearerToken(viewer.id),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.statusCode").value(403))
    }

    @Test
    @DisplayName("상대와 나의 퀴즈 답변 일치 개수를 조회한다")
    fun getUserAnswerMatch() {
        val viewer = memberRepository.save(Member(nickname = "조회자A").apply { activate() })
        val target = memberRepository.save(Member(nickname = "비교대상").apply { activate() })
        matchAccepted(viewer.id, target.id)

        val quizSet = quizSetRepository.save(QuizSetFixture.create())
        val quizIds = (1..3).map { order ->
            quizRepository.save(QuizFixture.create(quizSetId = quizSet.id, displayOrder = order)).id
        }
        completeQuizSet(viewer.id, quizSet.id, quizIds, choiceIds = listOf(1L, 2L, 3L))
        // 3문항 중 앞 2개만 같은 선택지를 골랐다.
        completeQuizSet(target.id, quizSet.id, quizIds, choiceIds = listOf(1L, 2L, 9L))

        mockMvc.perform(
            get("/api/v1/users/{id}/answers", target.id)
                .withApiKey()
                .withBearerToken(viewer.id),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.quizSetId").value(quizSet.id))
            .andExpect(jsonPath("$.data.matchedCount").value(2))
            .andExpect(jsonPath("$.data.totalCount").value(3))
            .andExpect(jsonPath("$.data.matchRate").value(66.7))
            .andDo(
                document(
                    "user-answer-match",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Users")
                            .summary("타인 답변 일치 조회 (\"나와 같은 답\")")
                            .description(
                                "상대와 나의 퀴즈 답변이 몇 개나 같은지 알려줍니다. 상대가 무엇을 골랐는지는 내려주지 않습니다. " +
                                    "기준은 두 사람이 함께 완주한 가장 최근 퀴즈셋이며, 그런 퀴즈셋이 없으면 quizSetId=null에 나머지는 0입니다. " +
                                    "matchRate는 매칭 점수와 같은 계산(일치 문항 ÷ 전체 문항 × 100, 소수점 1자리)입니다. " +
                                    "등급 라벨 문구와 그룹 평균 계산은 클라이언트가 처리합니다. " +
                                    "권한은 공개 프로필과 동일합니다(차단 관계면 403).",
                            )
                            .pathParameters(
                                parameterWithName("id").description("대상 사용자 ID"),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.quizSetId").type(JsonFieldType.NUMBER)
                                    .description("비교 기준 퀴즈셋 ID (함께 완주한 셋이 없으면 null)").optional(),
                                fieldWithPath("data.matchedCount").description("같은 선택지를 고른 문항 수"),
                                fieldWithPath("data.totalCount").description("비교한 전체 문항 수"),
                                fieldWithPath("data.matchRate").description("일치율 0~100 (소수점 1자리)"),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("함께 완주한 퀴즈셋이 없으면 빈 일치 요약을 반환한다")
    fun getUserAnswerMatchWithoutSharedQuizSet() {
        val viewer = memberRepository.save(Member(nickname = "조회자B").apply { activate() })
        val target = memberRepository.save(Member(nickname = "비교대상B").apply { activate() })
        matchAccepted(viewer.id, target.id)

        val quizSet = quizSetRepository.save(QuizSetFixture.create())
        val quizIds = listOf(quizRepository.save(QuizFixture.create(quizSetId = quizSet.id)).id)
        // 나만 완주했다.
        completeQuizSet(viewer.id, quizSet.id, quizIds, choiceIds = listOf(1L))

        mockMvc.perform(
            get("/api/v1/users/{id}/answers", target.id)
                .withApiKey()
                .withBearerToken(viewer.id),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.quizSetId").doesNotExist())
            .andExpect(jsonPath("$.data.matchedCount").value(0))
            .andExpect(jsonPath("$.data.totalCount").value(0))
            .andExpect(jsonPath("$.data.matchRate").value(0.0))
    }

    @Test
    @DisplayName("성별·나이 없이 가입하면 거부한다")
    fun registerWithoutGenderAndAge() {
        val member = memberRepository.save(Member(nickname = "임시닉네임3"))
        // 성별·나이는 매칭의 입력값이라 필수다 — 빠지면 후보 풀에서 조용히 제외되므로 가입 단계에서 막는다.
        val body = """
            {"nickname":"철수789","interests":["travel"],"location":"seoul","job":"it-tech","caricature":"m1"}
        """.trimIndent()

        mockMvc.perform(
            post("/api/v1/users")
                .withApiKey()
                .withBearerToken(member.id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("0001"))
    }

    @Test
    @DisplayName("가입 때 못 받은 신원 정보를 나중에 채운다")
    fun updatePersonalInfo() {
        val member = memberRepository.save(Member(nickname = "정보보완유저").apply { activate() })
        val request = UpdatePersonalInfoRequest(
            name = "김철수",
            phoneNumber = "010-1234-5678",
            email = "chulsoo@example.com",
            birthDate = LocalDateTime.of(1995, 3, 15, 0, 0),
        )

        mockMvc.perform(
            patch("/api/v1/users/me/personal-info")
                .withApiKey()
                .withBearerToken(member.id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.name").value("김철수"))
            .andExpect(jsonPath("$.data.phoneNumber").value("010-1234-5678"))
            .andExpect(jsonPath("$.data.email").value("chulsoo@example.com"))
            .andExpect(jsonPath("$.data.birthDate").value("1995-03-15"))
            .andDo(
                document(
                    "user-personal-info-update",
                    preprocessRequest(prettyPrint()),
                    preprocessResponse(prettyPrint()),
                    resource(
                        ResourceSnippetParameters.builder()
                            .tag("Users")
                            .summary("신원 정보 보완")
                            .description(
                                "가입 때 받지 못한 신원 정보를 나중에 채웁니다. 모든 항목이 선택이며 보낸 항목만 반영되고, " +
                                    "몇 번이든 호출할 수 있습니다. 카카오 일반 앱에서는 이름·전화번호·이메일·생년월일이 " +
                                    "동의항목으로 제공되지 않으므로 이 API가 유일한 입력 경로입니다. " +
                                    "성별·나이는 가입 필수값이라 여기서 다루지 않습니다.",
                            )
                            .requestFields(
                                fieldWithPath("name").description("이름 (최대 50자)").optional(),
                                fieldWithPath("phoneNumber").description("전화번호 (010-0000-0000)").optional(),
                                fieldWithPath("email").description("이메일").optional(),
                                fieldWithPath("birthDate").description("생년월일 (과거 날짜)").optional(),
                            )
                            .responseFields(
                                fieldWithPath("success").description("성공 여부"),
                                fieldWithPath("data.email").description("이메일").optional(),
                                fieldWithPath("data.birthDate").description("생년월일").optional(),
                                fieldWithPath("data.name").description("이름").optional(),
                                fieldWithPath("data.phoneNumber").description("전화번호").optional(),
                                fieldWithPath("data.gender").description("성별 (MALE, FEMALE)").optional(),
                                fieldWithPath("error").description("에러 정보 (성공 시 null)"),
                            )
                            .build(),
                    ),
                ),
            )
    }

    @Test
    @DisplayName("신원 정보 보완은 보낸 항목만 바꾼다")
    fun updatePersonalInfoPartially() {
        val member = memberRepository.save(
            Member(nickname = "부분갱신유저", name = "이전이름", phoneNumber = "010-0000-0000").apply { activate() },
        )

        mockMvc.perform(
            patch("/api/v1/users/me/personal-info")
                .withApiKey()
                .withBearerToken(member.id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"phoneNumber":"010-9999-8888"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.phoneNumber").value("010-9999-8888"))
            // 보내지 않은 이름은 그대로 남는다.
            .andExpect(jsonPath("$.data.name").value("이전이름"))
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

    private var reviewerSequence = 1

    /** 두 회원을 매칭 성사(ACCEPTED) 상태로 만든다 — 프로필·보조 정보 열람 권한의 전제. */
    private fun matchAccepted(viewerId: Long, targetId: Long) {
        personalMatchRepository.save(
            PersonalMatchFixture.create(
                requesterId = viewerId,
                receiverId = targetId,
                status = PersonalMatchStatus.ACCEPTED,
            ),
        )
    }

    /** 다른 회원이 [reviewedMemberId]에게 남긴 확정 평가 하나를 만든다. */
    private fun saveReceivedAnswer(
        reviewedMemberId: Long,
        meetingStatus: MeetingStatus,
        rating: Int,
        comment: String?,
    ) {
        val author = memberRepository.save(Member(nickname = "평가자${reviewerSequence++}").apply { activate() })
        val review = memberReviewRepository.save(MemberReviewFixture.create(authorMemberId = author.id))
        val answer = reviewAnswerRepository.save(
            ReviewAnswerFixture.pending(memberReviewId = review.id, reviewedMemberId = reviewedMemberId),
        )
        answer.answer(ReviewAnswerContent.of(meetingStatus, rating, comment), LocalDateTime.now())
        reviewAnswerRepository.save(answer)
    }

    /** [memberId]가 퀴즈셋을 완주하고 각 문항에 [choiceIds]를 고른 상태로 만든다. */
    private fun completeQuizSet(
        memberId: Long,
        quizSetId: Long,
        quizIds: List<Long>,
        choiceIds: List<Long>,
    ) {
        val progress = QuizProgressFixture.create(
            memberId = memberId,
            quizSetId = quizSetId,
            totalCount = quizIds.size,
        )
        repeat(quizIds.size) { progress.recordAnswer() }
        quizProgressRepository.save(progress)

        quizIds.forEachIndexed { index, quizId ->
            quizAnswerRepository.save(
                QuizAnswerFixture.create(memberId = memberId, quizId = quizId, choiceId = choiceIds[index]),
            )
        }
    }
}
