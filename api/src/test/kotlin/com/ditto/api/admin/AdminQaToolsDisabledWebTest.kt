package com.ditto.api.admin

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.QuizProgressFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.system.repository.ServerTimeOverrideRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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
    private val quizProgressRepository: QuizProgressRepository,
    private val memberRepository: MemberRepository,
    private val serverTimeOverrideRepository: ServerTimeOverrideRepository,
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

    fun saveQuizSetWithParticipant(memberId: Long): Long {
        val quizSetId = quizSetRepository.save(QuizSetFixture.currentWeek()).id
        quizProgressRepository.save(QuizProgressFixture.create(memberId = memberId, quizSetId = quizSetId, totalCount = 1))
        return quizSetId
    }

    "QA 도구 스위치가 꺼져 있으면" - {
        "QA 화면을 열면 대시보드로 돌려보낸다" {
            listOf("/admin/qa", "/admin/qa/rooms/1", "/admin/dummy", "/admin/dummy/single", "/admin/time-override")
                .forEach { path ->
                    mockMvc.perform(get(path).with(authentication(admin)))
                        .andExpect(redirectedUrl("/admin"))
                        .andExpect(flash().attribute("error", containsString("QA 도구가 꺼져 있습니다")))
                }
        }

        "더미 전체 삭제 요청은 거부하고 더미를 남긴다" {
            val dummyId = memberRepository.save(MemberFixture.create(nickname = "dummy-female-1")).id

            mockMvc.perform(post("/admin/dummy/clear").with(authentication(admin)).with(csrf()))
                .andExpect(redirectedUrl("/admin"))

            memberRepository.existsById(dummyId) shouldBe true
        }

        "시간 조정 요청은 거부하고 서버 시각을 바꾸지 않는다" {
            mockMvc.perform(
                post("/admin/time-override").param("dateTime", "2026-06-18T09:00")
                    .with(authentication(admin)).with(csrf()),
            )
                .andExpect(redirectedUrl("/admin"))
            mockMvc.perform(
                post("/admin/qa/server-time").param("dateTime", "2026-06-18T09:00")
                    .with(authentication(admin)).with(csrf()),
            )
                .andExpect(redirectedUrl("/admin"))

            serverTimeOverrideRepository.findFirstByOrderByIdAsc() shouldBe null
        }

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
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attribute("error", containsString("QA 도구가 꺼져 있습니다")))

            personalMatchRepository.existsByQuizSetId(quizSetId) shouldBe true
        }

        "강제 삭제 요청은 거부하고 퀴즈셋을 남긴다" {
            val quizSetId = saveMatchedQuizSet()

            mockMvc.perform(
                post("/admin/quiz-sets/{id}/qa/force-delete", quizSetId).with(authentication(admin)).with(csrf()),
            )
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attribute("error", containsString("QA 도구가 꺼져 있습니다")))

            quizSetRepository.existsById(quizSetId) shouldBe true
            personalMatchRepository.existsByQuizSetId(quizSetId) shouldBe true
        }

        "답·진행 초기화 요청은 거부하고 진행을 남긴다" {
            val quizSetId = saveQuizSetWithParticipant(memberId = 1L)

            mockMvc.perform(
                post("/admin/quiz-sets/{id}/qa/reset-answers", quizSetId).with(authentication(admin)).with(csrf()),
            )
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attribute("error", containsString("QA 도구가 꺼져 있습니다")))

            quizProgressRepository.findByMemberIdAndQuizSetId(1L, quizSetId) shouldNotBe null
        }

        "참여 현황에 행별 초기화 버튼이 없고 회원별 초기화 요청은 거부한다" {
            val quizSetId = saveQuizSetWithParticipant(memberId = 1L)

            mockMvc.perform(get("/admin/quiz-sets/{id}/participants", quizSetId).with(authentication(admin)))
                .andExpect(status().isOk)
                .andExpect(content().string(not(containsString("reset-answers"))))
            mockMvc.perform(
                post("/admin/quiz-sets/{id}/qa/members/{memberId}/reset-answers", quizSetId, 1L)
                    .with(authentication(admin)).with(csrf()),
            )
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attribute("error", containsString("QA 도구가 꺼져 있습니다")))

            quizProgressRepository.findByMemberIdAndQuizSetId(1L, quizSetId) shouldNotBe null
        }
    }
})
