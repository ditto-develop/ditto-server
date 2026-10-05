package com.ditto.api.admin.quiz

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.repository.QuizSetRepository
import io.kotest.matchers.shouldBe
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import javax.sql.DataSource

// 운영 기본값과 같게 스위치를 끈 컨텍스트. 다른 테스트는 application-test.yml 에서 켜 둔다.
@AutoConfigureMockMvc
@TestPropertySource(properties = ["ditto.admin.qa-tools.enabled=false"])
class AdminQaToolsDisabledWebTest(
    private val mockMvc: MockMvc,
    private val quizSetRepository: QuizSetRepository,
    private val personalMatchRepository: PersonalMatchRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val admin = UsernamePasswordAuthenticationToken(
        AdminPrincipal(1L, "관리자", "admin@ditto.pics"),
        null,
        listOf(SimpleGrantedAuthority("ROLE_ADMIN")),
    )

    fun saveMatchedQuizSet(): Long {
        val quizSetId = quizSetRepository.save(QuizSetFixture.currentWeek()).id
        personalMatchRepository.save(PersonalMatchFixture.create(1L, 2L, quizSetId))
        return quizSetId
    }

    "QA 도구 스위치가 꺼져 있으면" - {
        "매칭이 끝난 퀴즈셋 상세에도 QA 도구 카드와 안내가 없다" {
            val quizSetId = saveMatchedQuizSet()

            mockMvc.perform(get("/admin/quiz-sets/{id}", quizSetId).with(authentication(admin)))
                .andExpect(status().isOk)
                .andExpect(content().string(not(containsString("id=\"qa-tools\""))))
                .andExpect(content().string(not(containsString("href=\"#qa-tools\""))))
        }

        "초기화 요청은 거부하고 아무것도 지우지 않는다" {
            val quizSetId = saveMatchedQuizSet()

            mockMvc.perform(
                post("/admin/quiz-sets/{id}/qa/reset-matching", quizSetId).with(authentication(admin)).with(csrf()),
            )
                .andExpect(redirectedUrl("/admin/quiz-sets/$quizSetId"))
                .andExpect(flash().attribute("error", containsString("QA 도구가 꺼져 있습니다")))

            personalMatchRepository.existsByQuizSetId(quizSetId) shouldBe true
        }

        "강제 삭제 요청은 거부하고 퀴즈셋을 남긴다" {
            val quizSetId = saveMatchedQuizSet()

            mockMvc.perform(
                post("/admin/quiz-sets/{id}/qa/force-delete", quizSetId).with(authentication(admin)).with(csrf()),
            )
                .andExpect(redirectedUrl("/admin/quiz-sets/$quizSetId"))
                .andExpect(flash().attribute("error", containsString("QA 도구가 꺼져 있습니다")))

            quizSetRepository.existsById(quizSetId) shouldBe true
            personalMatchRepository.existsByQuizSetId(quizSetId) shouldBe true
        }
    }
})
