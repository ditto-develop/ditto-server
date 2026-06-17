package com.ditto.api.admin

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.MemberRole
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.QuizChoiceFixture
import com.ditto.domain.quiz.QuizFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.repository.QuizChoiceRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.socialaccount.entity.SocialAccount
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.domain.socialaccount.repository.SocialAccountRepository
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
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
                .param("year", "2026").param("month", "6").param("week", "3")
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
    @DisplayName("로그인 페이지 에러/로그아웃 메시지 분기")
    fun loginPageMessages() {
        mockMvc.perform(get("/admin/login").param("error", "")).andExpect(status().isOk)
        mockMvc.perform(get("/admin/login").param("logout", "")).andExpect(status().isOk)
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
                .param("year", "2026").param("month", "6").param("week", "3")
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
}
