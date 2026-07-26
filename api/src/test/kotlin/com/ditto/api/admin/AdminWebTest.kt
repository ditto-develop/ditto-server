package com.ditto.api.admin

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.MemberRole
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.memberreport.MemberReportFixture
import com.ditto.domain.memberreport.repository.MemberReportRepository
import com.ditto.domain.quiz.QuizChoiceFixture
import com.ditto.domain.quiz.QuizFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.repository.QuizChoiceRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.socialaccount.entity.SocialAccount
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.domain.socialaccount.repository.SocialAccountRepository
import org.hamcrest.CoreMatchers.containsString
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local", "test")
@Transactional
class AdminWebTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var quizSetRepository: QuizSetRepository

    @Autowired
    lateinit var quizRepository: QuizRepository

    @Autowired
    lateinit var quizChoiceRepository: QuizChoiceRepository

    @Autowired
    lateinit var memberRepository: MemberRepository

    @Autowired
    lateinit var socialAccountRepository: SocialAccountRepository

    @Autowired
    lateinit var memberReportRepository: MemberReportRepository

    private fun admin(): Authentication =
        UsernamePasswordAuthenticationToken(
            AdminPrincipal(1L, "관리자", "admin@ditto.pics"),
            null,
            listOf(SimpleGrantedAuthority("ROLE_ADMIN")),
        )

    @Test
    @DisplayName("로그인 페이지는 인증 없이 열린다")
    fun loginPage() {
        mockMvc.perform(get("/admin/login")).andExpect(status().isOk)
    }

    @Test
    @DisplayName("미인증 사용자는 로그인으로 리다이렉트된다")
    fun unauthenticatedRedirects() {
        mockMvc.perform(get("/admin")).andExpect(status().is3xxRedirection)
    }

    @Test
    @DisplayName("대시보드/퀴즈/시간/매칭 페이지가 렌더링된다")
    fun pagesRender() {
        mockMvc.perform(get("/admin").with(authentication(admin()))).andExpect(status().isOk)
        mockMvc.perform(get("/admin/quiz-sets").with(authentication(admin()))).andExpect(status().isOk)
        mockMvc.perform(get("/admin/quiz-sets/new").with(authentication(admin()))).andExpect(status().isOk)
        mockMvc.perform(get("/admin/time-override").with(authentication(admin()))).andExpect(status().isOk)
        mockMvc.perform(get("/admin/matching").with(authentication(admin()))).andExpect(status().isOk)
    }

    @Test
    @DisplayName("퀴즈셋 생성 후 상세를 조회한다")
    fun createAndDetail() {
        mockMvc.perform(
            post("/admin/quiz-sets")
                .with(authentication(admin())).with(csrf())
                .param("category", "성격").param("title", "테스트 퀴즈셋")
                .param("description", "설명")
                .param("startDate", "2026-06-15T00:00").param("endDate", "2026-06-21T23:59")
                .param("matchingType", "ONE_TO_ONE").param("isActive", "true"),
        ).andExpect(status().is3xxRedirection)

        val quizSet = quizSetRepository.save(QuizSetFixture.create())
        val quiz = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id, displayOrder = 1))
        quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id, displayOrder = 1))

        mockMvc.perform(get("/admin/quiz-sets/{id}", quizSet.id).with(authentication(admin())))
            .andExpect(status().isOk)
    }

    @Test
    @DisplayName("퀴즈셋 활성/비활성/하위 추가/삭제")
    fun quizMutations() {
        val quizSet = quizSetRepository.save(QuizSetFixture.create(isActive = false))
        val id = quizSet.id

        mockMvc.perform(post("/admin/quiz-sets/{id}/activate", id).with(authentication(admin())).with(csrf()))
            .andExpect(status().is3xxRedirection)
        mockMvc.perform(post("/admin/quiz-sets/{id}/deactivate", id).with(authentication(admin())).with(csrf()))
            .andExpect(status().is3xxRedirection)
        mockMvc.perform(
            post("/admin/quiz-sets/{id}/quizzes", id).with(authentication(admin())).with(csrf())
                .param("question", "질문?").param("displayOrder", "1"),
        ).andExpect(status().is3xxRedirection)

        val quiz = quizRepository.findByQuizSetIdOrderByDisplayOrderAsc(id).first()
        mockMvc.perform(
            post("/admin/quiz-sets/{id}/quizzes/{qid}/choices", id, quiz.id).with(authentication(admin())).with(csrf())
                .param("content", "선택지").param("displayOrder", "1"),
        ).andExpect(status().is3xxRedirection)
        mockMvc.perform(
            post("/admin/quiz-sets/{id}/quizzes/{qid}/update", id, quiz.id).with(authentication(admin())).with(csrf())
                .param("question", "수정된 질문").param("displayOrder", "2"),
        ).andExpect(status().is3xxRedirection)
        mockMvc.perform(post("/admin/quiz-sets/{id}/quizzes/{qid}/delete", id, quiz.id).with(authentication(admin())).with(csrf()))
            .andExpect(status().is3xxRedirection)
        mockMvc.perform(post("/admin/quiz-sets/{id}/delete", id).with(authentication(admin())).with(csrf()))
            .andExpect(status().is3xxRedirection)
    }

    @Test
    @DisplayName("시간 오버라이드 설정/해제")
    fun timeOverride() {
        mockMvc.perform(
            post("/admin/time-override").with(authentication(admin())).with(csrf())
                .param("dateTime", "2026-06-18T09:00"),
        ).andExpect(status().is3xxRedirection)
        mockMvc.perform(post("/admin/time-override/disable").with(authentication(admin())).with(csrf()))
            .andExpect(status().is3xxRedirection)
    }

    @Test
    @DisplayName("매칭 배치 수동 실행")
    fun runScheduledMatching() {
        mockMvc.perform(post("/admin/matching/run-scheduled").with(authentication(admin())).with(csrf()))
            .andExpect(status().is3xxRedirection)
    }

    @Test
    @DisplayName("더미 생성 페이지 렌더 + 생성/삭제")
    fun dummyPage() {
        mockMvc.perform(get("/admin/dummy").with(authentication(admin()))).andExpect(status().isOk)

        val quizSet = quizSetRepository.save(QuizSetFixture.create())
        val quiz = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id, displayOrder = 1))
        quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id, content = "A", displayOrder = 1))
        quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id, content = "B", displayOrder = 2))

        mockMvc.perform(
            post("/admin/dummy").with(authentication(admin())).with(csrf())
                .param("quizSetId", quizSet.id.toString())
                .param("maleCount", "2").param("femaleCount", "2")
                .param("minAge", "20").param("maxAge", "30")
                .param("preferredGender", "OPPOSITE"),
        ).andExpect(status().is3xxRedirection)

        mockMvc.perform(post("/admin/dummy/clear").with(authentication(admin())).with(csrf()))
            .andExpect(status().is3xxRedirection)
    }

    @Test
    @DisplayName("회원 관리 페이지 — 검색 전/검색 결과 렌더")
    fun memberSearchPage() {
        // 검색 전 빈 상태
        mockMvc.perform(get("/admin/members").with(authentication(admin()))).andExpect(status().isOk)

        // 같은 이메일을 가진 회원 2명
        memberRepository.save(MemberFixture.create(nickname = "m1", email = "dup@ditto.pics", role = MemberRole.USER))
        memberRepository.save(MemberFixture.create(nickname = "m2", email = "dup@ditto.pics", role = MemberRole.ADMIN))

        mockMvc.perform(get("/admin/members").param("email", "dup@ditto.pics").with(authentication(admin())))
            .andExpect(status().isOk)
    }

    @Test
    @DisplayName("회원 권한 변경 후 검색어 유지 리다이렉트")
    fun memberRoleChange() {
        val member = memberRepository.save(
            MemberFixture.create(nickname = "rolechg", email = "role@ditto.pics", role = MemberRole.USER),
        )

        mockMvc.perform(
            post("/admin/members/{id}/role", member.id).with(authentication(admin())).with(csrf())
                .param("role", "ADMIN").param("email", "role@ditto.pics"),
        ).andExpect(status().is3xxRedirection)
    }

    @Test
    @DisplayName("카카오 로그인 진입은 인가 URL로 리다이렉트된다")
    fun oauthAuthorizeRedirect() {
        mockMvc.perform(get("/admin/oauth/kakao")).andExpect(status().is3xxRedirection)
    }

    @Test
    @DisplayName("ADMIN 회원 카카오 콜백은 세션 설정 후 대시보드로 이동한다")
    fun oauthCallbackAdmin() {
        val member = memberRepository.save(MemberFixture.create(role = MemberRole.ADMIN).apply { activate() })
        socialAccountRepository.save(
            SocialAccount.create(memberId = member.id, provider = SocialProvider.KAKAO, providerUserId = "12345"),
        )

        mockMvc.perform(get("/admin/oauth/kakao/callback").param("code", "test-code"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/admin"))
    }

    @Test
    @DisplayName("미등록 회원 카카오 콜백은 로그인 에러로 이동한다")
    fun oauthCallbackDenied() {
        mockMvc.perform(get("/admin/oauth/kakao/callback").param("code", "test-code"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/admin/login?error"))
    }

    @Test
    @DisplayName("로컬 개발 로그인은 세션 설정 후 대시보드로 이동한다")
    fun devLoginRedirect() {
        mockMvc.perform(get("/admin/oauth/dev"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/admin"))
    }

    @Test
    @DisplayName("로컬 개발 로그인 세션으로 어드민 페이지에 접근할 수 있다")
    fun devLoginSessionGrantsAccess() {
        val session = mockMvc.perform(get("/admin/oauth/dev"))
            .andReturn().request.session as MockHttpSession

        mockMvc.perform(get("/admin").session(session)).andExpect(status().isOk)
    }

    @Test
    @DisplayName("local 프로파일에서 로그인 페이지에 로컬 개발 로그인 버튼이 노출된다")
    fun loginPageShowsDevLoginButton() {
        mockMvc.perform(get("/admin/login"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("로컬 개발 로그인")))
    }

    @Test
    @DisplayName("로그인 페이지 에러/로그아웃 메시지 분기")
    fun loginPageMessages() {
        mockMvc.perform(get("/admin/login").param("error", "")).andExpect(status().isOk)
        mockMvc.perform(get("/admin/login").param("logout", "")).andExpect(status().isOk)
    }

    @Test
    @DisplayName("퀴즈셋 수정 폼의 시작/종료일시가 datetime-local 형식으로 렌더링된다")
    fun editFormRendersDateTimeLocalValues() {
        val quizSet = quizSetRepository.save(QuizSetFixture.create())

        mockMvc.perform(get("/admin/quiz-sets/{id}/edit", quizSet.id).with(authentication(admin())))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("value=\"2026-04-06T00:00\"")))
            .andExpect(content().string(containsString("value=\"2026-04-12T23:59\"")))
    }

    @Test
    @DisplayName("퀴즈셋 수정/편집 폼 + 선택지 수정/삭제 + 매칭 재생성")
    fun quizUpdateAndRegenerate() {
        val quizSet = quizSetRepository.save(QuizSetFixture.create())
        val id = quizSet.id

        mockMvc.perform(get("/admin/quiz-sets/{id}/edit", id).with(authentication(admin())))
            .andExpect(status().isOk)
        mockMvc.perform(
            post("/admin/quiz-sets/{id}", id).with(authentication(admin())).with(csrf())
                .param("category", "수정").param("title", "수정 제목").param("description", "d")
                .param("startDate", "2026-06-15T00:00").param("endDate", "2026-06-21T23:59")
                .param("matchingType", "ONE_TO_ONE").param("isActive", "false"),
        ).andExpect(status().is3xxRedirection)

        val quiz = quizRepository.save(QuizFixture.create(quizSetId = id, displayOrder = 1))
        val choice = quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id, displayOrder = 1))
        mockMvc.perform(
            post("/admin/quiz-sets/{id}/quizzes/{qid}/choices/{cid}/update", id, quiz.id, choice.id)
                .with(authentication(admin())).with(csrf())
                .param("content", "수정 선택지").param("displayOrder", "2"),
        ).andExpect(status().is3xxRedirection)
        mockMvc.perform(
            post("/admin/quiz-sets/{id}/quizzes/{qid}/choices/{cid}/delete", id, quiz.id, choice.id)
                .with(authentication(admin())).with(csrf()),
        ).andExpect(status().is3xxRedirection)

        mockMvc.perform(post("/admin/matching/quiz-sets/{id}/regenerate", id).with(authentication(admin())).with(csrf()))
            .andExpect(status().is3xxRedirection)
    }

    @Test
    @DisplayName("신고 목록·상세 페이지가 렌더링된다")
    fun reportPages() {
        val reporter = memberRepository.save(MemberFixture.create(nickname = "신고자", status = MemberStatus.ACTIVE))
        val reported = memberRepository.save(MemberFixture.create(nickname = "피신고자", status = MemberStatus.ACTIVE))
        val report = memberReportRepository.save(
            MemberReportFixture.create(reporterId = reporter.id, reportedMemberId = reported.id),
        )

        mockMvc.perform(get("/admin/reports").with(authentication(admin()))).andExpect(status().isOk)
        mockMvc.perform(get("/admin/reports/{id}", report.id).with(authentication(admin()))).andExpect(status().isOk)
    }

    @Test
    @DisplayName("신고 검토 처리 후 상세로 리다이렉트된다")
    fun reviewReport() {
        val reporter = memberRepository.save(MemberFixture.create(nickname = "신고자2", status = MemberStatus.ACTIVE))
        val reported = memberRepository.save(MemberFixture.create(nickname = "피신고자2", status = MemberStatus.ACTIVE))
        val report = memberReportRepository.save(
            MemberReportFixture.create(reporterId = reporter.id, reportedMemberId = reported.id),
        )

        mockMvc.perform(
            post("/admin/reports/{id}/action", report.id)
                .with(authentication(admin())).with(csrf())
                .param("decision", "REJECT").param("reviewNote", "근거 부족"),
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/admin/reports/" + report.id))
    }

    @Test
    @DisplayName("회원 제재 관리 페이지 렌더·직권 제재·해제")
    fun memberSanctions() {
        val member = memberRepository.save(MemberFixture.create(nickname = "제재대상", status = MemberStatus.ACTIVE))

        mockMvc.perform(get("/admin/members/{id}/sanctions", member.id).with(authentication(admin())))
            .andExpect(status().isOk)

        mockMvc.perform(
            post("/admin/members/{id}/sanctions", member.id)
                .with(authentication(admin())).with(csrf())
                .param("level", "SUSPENSION").param("origin", "MANUAL").param("note", "직권"),
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/admin/members/" + member.id + "/sanctions"))
    }
}
