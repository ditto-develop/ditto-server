package com.ditto.api.admin

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.match.GroupMatchFixture
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.QuizProgressFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.system.entity.ServerTimeOverride
import com.ditto.domain.system.repository.ServerTimeOverrideRepository
import io.kotest.assertions.withClue
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
import java.time.LocalDateTime
import javax.sql.DataSource

private const val DISABLED_NOTICE = "QA 도구(더미·QA 콘솔·시간 조정·퀴즈셋 초기화)가 꺼져 있습니다"

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
    private val groupMatchRepository: GroupMatchRepository,
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
        "QA 화면을 열면 안내 문구와 함께 대시보드로 돌려보낸다" {
            listOf("/admin/qa", "/admin/qa/rooms/1", "/admin/dummy", "/admin/dummy/single", "/admin/time-override")
                .forEach { path ->
                    withClue(path) {
                        mockMvc.perform(get(path).with(authentication(admin)))
                            .andExpect(redirectedUrl("/admin"))
                            .andExpect(flash().attribute("error", containsString(DISABLED_NOTICE)))
                    }
                }
        }

        "사이드바와 대시보드에 QA 화면 링크·켜짐 배지·시간 조정 안내가 없고 매칭 실행은 남는다" {
            mockMvc.perform(get("/admin").with(authentication(admin)))
                .andExpect(status().isOk)
                .andExpect(content().string(not(containsString("href=\"/admin/qa\""))))
                .andExpect(content().string(not(containsString("href=\"/admin/dummy\""))))
                .andExpect(content().string(not(containsString("href=\"/admin/time-override\""))))
                .andExpect(content().string(not(containsString("QA 도구 켜짐"))))
                .andExpect(content().string(not(containsString("시간 조정을 켜 두면"))))
                .andExpect(content().string(containsString("href=\"/admin/matching\"")))
        }

        "매칭 실행 화면과 재생성 실패 안내가 꺼진 QA 도구를 가리키지 않는다" {
            val quizSet = quizSetRepository.save(QuizSetFixture.create(matchingType = MatchingType.GROUP))
            groupMatchRepository.save(GroupMatchFixture.create(quizSetId = quizSet.id, acceptedCount = 3))

            mockMvc.perform(get("/admin/matching").with(authentication(admin)))
                .andExpect(status().isOk)
                .andExpect(content().string(not(containsString("시간 조정 중에는"))))
            mockMvc.perform(
                post("/admin/matching/quiz-sets/{id}/regenerate", quizSet.id).with(authentication(admin)).with(csrf()),
            )
                .andExpect(redirectedUrl("/admin/matching"))
                .andExpect(flash().attribute("error", containsString("매칭 재생성 실패")))
                .andExpect(flash().attribute("error", not(containsString("매칭 기록 초기화"))))
        }

        "더미 전체 삭제 요청은 거부하고 더미를 남긴다" {
            val dummyId = memberRepository.save(MemberFixture.create(nickname = "dummy-female-1")).id

            mockMvc.perform(post("/admin/dummy/clear").with(authentication(admin)).with(csrf()))
                .andExpect(redirectedUrl("/admin"))

            memberRepository.existsById(dummyId) shouldBe true
        }

        "시간 조정 화면과 QA 콘솔의 시각 변경 요청은 거부하고 조정 값을 저장하지 않는다" {
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

        "시간 조정 해제 요청도 거부해 남은 값을 건드리지 않는다" {
            serverTimeOverrideRepository.save(
                ServerTimeOverride.disabled().apply { override(LocalDateTime.of(2026, 6, 18, 9, 0), null, null) },
            )

            listOf("/admin/time-override/disable", "/admin/qa/server-time/disable").forEach { path ->
                withClue(path) {
                    mockMvc.perform(post(path).with(authentication(admin)).with(csrf()))
                        .andExpect(redirectedUrl("/admin"))
                }
            }

            serverTimeOverrideRepository.findFirstByOrderByIdAsc()?.enabled shouldBe true
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
                .andExpect(flash().attribute("error", containsString(DISABLED_NOTICE)))

            personalMatchRepository.existsByQuizSetId(quizSetId) shouldBe true
        }

        "강제 삭제 요청은 거부하고 퀴즈셋을 남긴다" {
            val quizSetId = saveMatchedQuizSet()

            mockMvc.perform(
                post("/admin/quiz-sets/{id}/qa/force-delete", quizSetId).with(authentication(admin)).with(csrf()),
            )
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attribute("error", containsString(DISABLED_NOTICE)))

            quizSetRepository.existsById(quizSetId) shouldBe true
            personalMatchRepository.existsByQuizSetId(quizSetId) shouldBe true
        }

        "답·진행 초기화 요청은 거부하고 진행을 남긴다" {
            val quizSetId = saveQuizSetWithParticipant(memberId = 1L)

            mockMvc.perform(
                post("/admin/quiz-sets/{id}/qa/reset-answers", quizSetId).with(authentication(admin)).with(csrf()),
            )
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attribute("error", containsString(DISABLED_NOTICE)))

            quizProgressRepository.findByMemberIdAndQuizSetId(1L, quizSetId) shouldNotBe null
        }

        "참여 현황에 행별 초기화 버튼과 더미 만들기 링크가 없고 회원별 초기화 요청은 거부한다" {
            val quizSetId = saveQuizSetWithParticipant(memberId = 1L)

            mockMvc.perform(get("/admin/quiz-sets/{id}/participants", quizSetId).with(authentication(admin)))
                .andExpect(status().isOk)
                .andExpect(content().string(not(containsString("reset-answers"))))
                .andExpect(content().string(not(containsString("/admin/dummy/single"))))
            mockMvc.perform(
                post("/admin/quiz-sets/{id}/qa/members/{memberId}/reset-answers", quizSetId, 1L)
                    .with(authentication(admin)).with(csrf()),
            )
                .andExpect(redirectedUrl("/admin"))
                .andExpect(flash().attribute("error", containsString(DISABLED_NOTICE)))

            quizProgressRepository.findByMemberIdAndQuizSetId(1L, quizSetId) shouldNotBe null
        }
    }
})
