package com.ditto.api.admin.quiz

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.chat.ChatRoomFixture
import com.ditto.domain.chat.entity.ChatMessage
import com.ditto.domain.chat.entity.ChatQuizHint
import com.ditto.domain.chat.repository.ChatMessageRepository
import com.ditto.domain.chat.repository.ChatQuizHintRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.entity.QuizSetStat
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.quiz.repository.QuizSetStatRepository
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.hamcrest.CoreMatchers.containsString
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import javax.sql.DataSource

/** IMP-1 이 더한 어드민 도구 — 힌트 질문 입력 검증, 통계 스냅샷 다시 만들기, QA 힌트 다시 만들기. */
@AutoConfigureMockMvc
class AdminQuizChatToolsWebTest(
    private val mockMvc: MockMvc,
    private val quizSetRepository: QuizSetRepository,
    private val quizSetStatRepository: QuizSetStatRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatQuizHintRepository: ChatQuizHintRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val admin = UsernamePasswordAuthenticationToken(
        AdminPrincipal(5L, "관리자", "admin@ditto.pics"),
        null,
        listOf(SimpleGrantedAuthority("ROLE_ADMIN")),
    )

    "대화 힌트 질문이 200자를 넘으면 500 이 아니라 폼으로 돌아와 한국어 오류를 보여 준다" {
        mockMvc.perform(
            post("/admin/quiz-sets").with(authentication(admin)).with(csrf())
                .param("category", "성격").param("title", "힌트 길이")
                .param("weekStartedOn", "2026-06-15").param("matchingType", "ONE_TO_ONE")
                .param("quizzes[0].question", "치약 짤 때?")
                .param("quizzes[0].hintQuestion", "가".repeat(201))
                .param("quizzes[0].choices[0].content", "아래부터")
                .param("quizzes[0].choices[1].content", "중간부터"),
        )
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("1번 문항의 대화 힌트 질문이 200자를 넘습니다.")))
    }

    "통계 스냅샷 다시 만들기는 스냅샷을 지우고 상세로 돌아간다" {
        val quizSet = quizSetRepository.save(QuizSetFixture.create())
        quizSetStatRepository.save(QuizSetStat.of(quizSet.id, participantCount = 3, overlapThreshold = 2))

        mockMvc.perform(post("/admin/quiz-sets/{id}/stat-snapshot/reset", quizSet.id).with(authentication(admin)).with(csrf()))
            .andExpect(redirectedUrl("/admin/quiz-sets/${quizSet.id}"))
            .andExpect(flash().attribute("message", containsString("다시 만듭니다")))

        quizSetStatRepository.findByQuizSetId(quizSet.id).shouldBeNull()
    }

    "QA 힌트 다시 만들기는 방의 힌트 표시와 메시지를 지운다" {
        val room = chatRoomRepository.save(ChatRoomFixture.personal())
        val hint = chatMessageRepository.save(ChatMessage.quizHint(room.id, """{"kind":"SAME"}"""))
        chatQuizHintRepository.save(ChatQuizHint.of(room.id, hint.id))

        mockMvc.perform(post("/admin/qa/rooms/{id}/hint/reset", room.id).with(authentication(admin)).with(csrf()))
            .andExpect(flash().attribute("message", containsString("1분 안에 다시 만들어집니다")))

        chatQuizHintRepository.existsByRoomId(room.id) shouldBe false
        chatMessageRepository.findAll().size shouldBe 0
    }
})
