package com.ditto.api.admin.stats

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.QuizAnswerFixture
import com.ditto.domain.quiz.QuizChoiceFixture
import com.ditto.domain.quiz.QuizFixture
import com.ditto.domain.quiz.QuizProgressFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.repository.QuizAnswerRepository
import com.ditto.domain.quiz.repository.QuizChoiceRepository
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import io.kotest.matchers.string.shouldContain
import org.hamcrest.CoreMatchers.containsString
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime
import javax.sql.DataSource

@AutoConfigureMockMvc
class AdminStatsWebTest(
    private val mockMvc: MockMvc,
    private val quizSetRepository: QuizSetRepository,
    private val memberRepository: MemberRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val quizRepository: QuizRepository,
    private val quizChoiceRepository: QuizChoiceRepository,
    private val quizAnswerRepository: QuizAnswerRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val admin = UsernamePasswordAuthenticationToken(
        AdminPrincipal(1L, "관리자", "admin@ditto.pics"),
        null,
        listOf(SimpleGrantedAuthority("ROLE_ADMIN")),
    )

    // 필드 이름은 stats-calc.js 가 읽는다. JSON 이 JS 문자열 안에 들어가 따옴표는 \" 로, 한글은 \uXXXX 로 나온다.
    fun pageJsonContaining(json: String) = containsString(json.replace("\"", "\\\""))

    "운영 지표 화면" - {
        "퀴즈셋이 있는 주를 회차로 보여 주고 집계 결과를 페이지 JSON 에 넣는다" {
            val quizSet = quizSetRepository.save(QuizSetFixture.create())
            val member = memberRepository.save(
                MemberFixture.create(status = MemberStatus.ACTIVE, joinedAt = LocalDateTime.of(2026, 4, 7, 10, 0)),
            )
            quizProgressRepository.save(
                QuizProgressFixture.create(memberId = member.id, quizSetId = quizSet.id, totalCount = 1)
                    .apply { recordAnswer() },
            )
            val quiz = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id, question = "Q1"))
            val choice = quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id, content = "A"))
            quizAnswerRepository.save(
                QuizAnswerFixture.create(memberId = member.id, quizId = quiz.id, choiceId = choice.id),
            )

            mockMvc.perform(get("/admin/stats").param("week", "2026-04-08").with(authentication(admin)))
                .andExpect(status().isOk)
                .andExpect(content().string(containsString("1회차 · 4/6 주")))
                .andExpect(content().string(pageJsonContaining(""""countsByStatus":{"ACTIVE":1}""")))
                .andExpect(content().string(pageJsonContaining(""""recentJoinedAts":["2026-04-07 10:00:00"]""")))
                .andExpect(
                    content().string(
                        pageJsonContaining(""""genderBirthDateCounts":[{"gender":null,"birthDate":null,"count":1}]"""),
                    ),
                )
                .andExpect(
                    content().string(
                        pageJsonContaining(
                            """"progressCounts":[{"matchingType":"ONE_TO_ONE","status":"COMPLETED",""" +
                                """"gender":null,"count":1}],"activeRoundWeeks":["2026-04-06"],"activeMemberCount":1""",
                        ),
                    ),
                )
                .andExpect(
                    content().string(
                        pageJsonContaining(
                            """"choiceAnswerCounts":[{"quizSetId":${quizSet.id},"matchingType":"ONE_TO_ONE",""" +
                                """"quizId":${quiz.id},"quizDisplayOrder":1,"question":"Q1",""" +
                                """"choiceId":${choice.id},""" +
                                """"choiceDisplayOrder":1,"choiceContent":"A","answerCount":1}]""",
                        ),
                    ),
                )
        }

        "비활성 퀴즈셋만 있는 주는 회차 번호가 없다" {
            quizSetRepository.save(QuizSetFixture.create(isActive = false))

            mockMvc.perform(get("/admin/stats").param("week", "2026-04-06").with(authentication(admin)))
                .andExpect(status().isOk)
                .andExpect(content().string(containsString("회차 없음 · 4/6 주")))
        }

        "빈 필터 값을 넘겨도 오류 없이 매칭 유형 전체로 열린다" {
            val html = mockMvc.perform(
                get("/admin/stats")
                    .param("matchingType", "")
                    .param("gender", "")
                    .param("ageBand", "")
                    .param("joinedWeek", "")
                    .with(authentication(admin)),
            )
                .andExpect(status().isOk)
                .andReturn().response.contentAsString

            html shouldContain Regex("""value="ALL"\s+selected="selected"""")
        }
    }
})
