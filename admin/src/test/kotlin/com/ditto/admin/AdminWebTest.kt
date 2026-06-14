package com.ditto.admin

import com.ditto.admin.auth.AdminPrincipal
import com.ditto.domain.quiz.QuizChoiceFixture
import com.ditto.domain.quiz.QuizFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.repository.QuizChoiceRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
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

    private fun admin(): Authentication =
        UsernamePasswordAuthenticationToken(
            AdminPrincipal(1L, "관리자", "admin@ditto.pics"),
            null,
            listOf(SimpleGrantedAuthority("ROLE_ADMIN")),
        )

    @Test
    @DisplayName("로그인 페이지는 인증 없이 열린다")
    fun loginPage() {
        mockMvc.perform(get("/login")).andExpect(status().isOk)
    }

    @Test
    @DisplayName("미인증 사용자는 로그인으로 리다이렉트된다")
    fun unauthenticatedRedirects() {
        mockMvc.perform(get("/")).andExpect(status().is3xxRedirection)
    }

    @Test
    @DisplayName("대시보드/퀴즈/시간/매칭 페이지가 렌더링된다")
    fun pagesRender() {
        mockMvc.perform(get("/").with(authentication(admin()))).andExpect(status().isOk)
        mockMvc.perform(get("/quiz-sets").with(authentication(admin()))).andExpect(status().isOk)
        mockMvc.perform(get("/quiz-sets/new").with(authentication(admin()))).andExpect(status().isOk)
        mockMvc.perform(get("/time-override").with(authentication(admin()))).andExpect(status().isOk)
        mockMvc.perform(get("/matching").with(authentication(admin()))).andExpect(status().isOk)
    }

    @Test
    @DisplayName("퀴즈셋 생성 후 상세를 조회한다")
    fun createAndDetail() {
        mockMvc.perform(
            post("/quiz-sets")
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

        mockMvc.perform(get("/quiz-sets/{id}", quizSet.id).with(authentication(admin())))
            .andExpect(status().isOk)
    }

    @Test
    @DisplayName("퀴즈셋 활성/비활성/하위 추가/삭제")
    fun quizMutations() {
        val quizSet = quizSetRepository.save(QuizSetFixture.create(isActive = false))
        val id = quizSet.id

        mockMvc.perform(post("/quiz-sets/{id}/activate", id).with(authentication(admin())).with(csrf()))
            .andExpect(status().is3xxRedirection)
        mockMvc.perform(post("/quiz-sets/{id}/deactivate", id).with(authentication(admin())).with(csrf()))
            .andExpect(status().is3xxRedirection)
        mockMvc.perform(
            post("/quiz-sets/{id}/quizzes", id).with(authentication(admin())).with(csrf())
                .param("question", "질문?").param("displayOrder", "1"),
        ).andExpect(status().is3xxRedirection)

        val quiz = quizRepository.findByQuizSetIdOrderByDisplayOrderAsc(id).first()
        mockMvc.perform(
            post("/quiz-sets/{id}/quizzes/{qid}/choices", id, quiz.id).with(authentication(admin())).with(csrf())
                .param("content", "선택지").param("displayOrder", "1"),
        ).andExpect(status().is3xxRedirection)
        mockMvc.perform(
            post("/quiz-sets/{id}/quizzes/{qid}/update", id, quiz.id).with(authentication(admin())).with(csrf())
                .param("question", "수정된 질문").param("displayOrder", "2"),
        ).andExpect(status().is3xxRedirection)
        mockMvc.perform(post("/quiz-sets/{id}/quizzes/{qid}/delete", id, quiz.id).with(authentication(admin())).with(csrf()))
            .andExpect(status().is3xxRedirection)
        mockMvc.perform(post("/quiz-sets/{id}/delete", id).with(authentication(admin())).with(csrf()))
            .andExpect(status().is3xxRedirection)
    }

    @Test
    @DisplayName("시간 오버라이드 설정/해제")
    fun timeOverride() {
        mockMvc.perform(
            post("/time-override").with(authentication(admin())).with(csrf())
                .param("dateTime", "2026-06-18T09:00"),
        ).andExpect(status().is3xxRedirection)
        mockMvc.perform(post("/time-override/disable").with(authentication(admin())).with(csrf()))
            .andExpect(status().is3xxRedirection)
    }

    @Test
    @DisplayName("매칭 배치 수동 실행")
    fun runScheduledMatching() {
        mockMvc.perform(post("/matching/run-scheduled").with(authentication(admin())).with(csrf()))
            .andExpect(status().is3xxRedirection)
    }
}
