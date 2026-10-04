package com.ditto.api.admin.dummy

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.Interest
import com.ditto.domain.member.entity.Location
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.QuizChoiceFixture
import com.ditto.domain.quiz.QuizFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.entity.QuizChoice
import com.ditto.domain.quiz.entity.QuizProgressStatus
import com.ditto.domain.quiz.repository.QuizAnswerRepository
import com.ditto.domain.quiz.repository.QuizChoiceRepository
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.hamcrest.CoreMatchers.containsString
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import javax.sql.DataSource

@AutoConfigureMockMvc
class AdminSingleDummyWebTest(
    private val mockMvc: MockMvc,
    private val quizSetRepository: QuizSetRepository,
    private val quizRepository: QuizRepository,
    private val quizChoiceRepository: QuizChoiceRepository,
    private val quizAnswerRepository: QuizAnswerRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val memberRepository: MemberRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val admin = UsernamePasswordAuthenticationToken(
        AdminPrincipal(1L, "관리자", "admin@ditto.pics"),
        null,
        listOf(SimpleGrantedAuthority("ROLE_ADMIN")),
    )

    fun MockHttpServletRequestBuilder.asAdmin() = with(authentication(admin)).with(csrf())

    class QuizSetSetup(val quizSetId: Long, val choicesByOrder: List<List<QuizChoice>>)

    fun setupQuizSet(): QuizSetSetup {
        val quizSet = quizSetRepository.save(QuizSetFixture.create(title = "한 명 만들기 셋"))
        val choicesByOrder = (1..2).map { order ->
            val quiz = quizRepository.save(
                QuizFixture.create(quizSetId = quizSet.id, question = "질문$order", displayOrder = order),
            )
            listOf("왼쪽$order", "오른쪽$order").mapIndexed { index, content ->
                quizChoiceRepository.save(
                    QuizChoiceFixture.create(quizId = quiz.id, content = content, displayOrder = index + 1),
                )
            }
        }
        return QuizSetSetup(quizSet.id, choicesByOrder)
    }

    fun createRequest(setup: QuizSetSetup): MockHttpServletRequestBuilder = post("/admin/dummy/single").asAdmin()
        .param("quizSetId", setup.quizSetId.toString())
        .param("gender", "FEMALE")
        .param("age", "30")
        .param("location", "BUSAN")
        .param("job", "DESIGN")
        .param("interests", "TRAVEL", "MUSIC")
        .param("avatarNumber", "")
        .param("answeredCount", "")

    "한 명 만들기 화면" - {
        "더미 페이지에서 퀴즈셋을 골라 들어가는 폼이 있다" {
            mockMvc.perform(get("/admin/dummy").with(authentication(admin)))
                .andExpect(status().isOk)
                .andExpect(content().string(containsString("더미 한 명 만들기")))
                .andExpect(content().string(containsString("/admin/dummy/single")))
        }

        "퀴즈셋의 문항·선택지와 프로필 라벨을 그린다" {
            val setup = setupQuizSet()

            val formPage = get("/admin/dummy/single").param("quizSetId", setup.quizSetId.toString())

            mockMvc.perform(formPage.with(authentication(admin)))
                .andExpect(status().isOk)
                .andExpect(content().string(containsString("한 명 만들기 셋")))
                .andExpect(content().string(containsString("질문2")))
                .andExpect(content().string(containsString("오른쪽2")))
                .andExpect(content().string(containsString("choiceIdByQuizId[${setup.choicesByOrder[1][1].quizId}]")))
                .andExpect(content().string(containsString("영화/드라마")))
                .andExpect(content().string(containsString("경기")))
                .andExpect(content().string(containsString("/admin/quiz-sets/${setup.quizSetId}/participants")))
        }

        "없는 퀴즈셋이면 더미 페이지로 돌려보낸다" {
            mockMvc.perform(get("/admin/dummy/single").param("quizSetId", "999999").with(authentication(admin)))
                .andExpect(redirectedUrl("/admin/dummy"))
                .andExpect(flash().attributeExists("error"))
        }
    }

    "한 명 만들기 제출" - {
        "폼 값으로 더미를 만들고 같은 퀴즈셋 폼으로 돌아간다. 무작위로 둔 문항도 답한다" {
            val setup = setupQuizSet()
            val chosen = setup.choicesByOrder[1][1]

            mockMvc.perform(
                createRequest(setup)
                    .param("nicknameSuffix", "웹테스트")
                    .param("choiceIdByQuizId[${setup.choicesByOrder[0][0].quizId}]", "")
                    .param("choiceIdByQuizId[${chosen.quizId}]", chosen.id.toString()),
            )
                .andExpect(redirectedUrl("/admin/dummy/single?quizSetId=${setup.quizSetId}"))
                .andExpect(flash().attribute("message", containsString("dummy-웹테스트")))

            val dummy = memberRepository.findByNicknameStartingWith("dummy-웹테스트").single()
            dummy.gender shouldBe Gender.FEMALE
            dummy.age shouldBe 30
            dummy.location shouldBe Location.BUSAN
            dummy.interests shouldBe setOf(Interest.TRAVEL, Interest.MUSIC)
            val quizIds = setup.choicesByOrder.map { it.first().quizId }
            val choiceIdByQuizId = quizAnswerRepository.findByMemberIdAndQuizIdIn(dummy.id, quizIds)
                .associate { it.quizId to it.choiceId }
            choiceIdByQuizId.keys shouldBe quizIds.toSet()
            choiceIdByQuizId[chosen.quizId] shouldBe chosen.id
            quizProgressRepository.findByMemberIdAndQuizSetId(dummy.id, setup.quizSetId)
                .shouldNotBeNull().status shouldBe QuizProgressStatus.COMPLETED
        }

        "거부되면 입력한 값을 그대로 둔 채 같은 폼에 사유를 보여 준다" {
            val setup = setupQuizSet()
            memberRepository.save(MemberFixture.create(nickname = "dummy-중복"))

            mockMvc.perform(createRequest(setup).param("nicknameSuffix", "중복"))
                .andExpect(status().isOk)
                .andExpect(content().string(containsString("이미 있는 닉네임입니다: dummy-중복")))
                .andExpect(content().string(containsString("value=\"중복\"")))
                .andExpect(content().string(containsString("value=\"30\"")))
                .andExpect(content().string(containsString("질문1")))
        }
    }
})
