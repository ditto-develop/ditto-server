package com.ditto.api.admin.qa

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.api.admin.qa.dto.QaConsoleView
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.match.MatchCandidateFixture
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.entity.PersonalMatch
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.entity.QuizSet
import com.ditto.domain.quiz.repository.QuizSetRepository
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.hamcrest.CoreMatchers.containsString
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import javax.sql.DataSource

@AutoConfigureMockMvc
class AdminQaWebTest(
    private val mockMvc: MockMvc,
    private val memberRepository: MemberRepository,
    private val quizSetRepository: QuizSetRepository,
    private val personalMatchRepository: PersonalMatchRepository,
    private val matchCandidateRepository: MatchCandidateRepository,
    private val chatRoomRepository: ChatRoomRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val admin = UsernamePasswordAuthenticationToken(
        AdminPrincipal(1L, "관리자", "admin@ditto.pics"),
        null,
        listOf(SimpleGrantedAuthority("ROLE_ADMIN")),
    )

    fun MockHttpServletRequestBuilder.asAdmin() = with(authentication(admin)).with(csrf())

    fun saveCurrentWeekQuizSet(): QuizSet = quizSetRepository.save(QuizSetFixture.currentWeek())

    fun saveMember(nickname: String): Member =
        memberRepository.save(
            MemberFixture.create(nickname = nickname, email = "$nickname@ditto.pics", status = MemberStatus.ACTIVE),
        )

    fun saveRequest(requester: Member, receiver: Member, quizSet: QuizSet): PersonalMatch =
        personalMatchRepository.save(
            PersonalMatchFixture.create(requesterId = requester.id, receiverId = receiver.id, quizSetId = quizSet.id),
        )

    val personalMatchUrl = "/admin/qa/dummies/{dummyId}/personal-matches/{matchId}"

    fun acceptAs(dummyId: Long, matchId: Long): ResultActions =
        mockMvc.perform(post("$personalMatchUrl/accept", dummyId, matchId).asAdmin())

    fun rejectAs(dummyId: Long, matchId: Long): ResultActions =
        mockMvc.perform(post("$personalMatchUrl/reject", dummyId, matchId).asAdmin())

    fun saveCandidatePair(quizSet: QuizSet, one: Member, other: Member) {
        matchCandidateRepository.save(
            MatchCandidateFixture.create(ownerMemberId = one.id, otherMemberId = other.id, quizSetId = quizSet.id),
        )
        matchCandidateRepository.save(
            MatchCandidateFixture.create(ownerMemberId = other.id, otherMemberId = one.id, quizSetId = quizSet.id),
        )
    }

    "콘솔 화면" - {
        "더미가 받은 신청과 더미가 아직 신청하지 않은 실회원 후보를 보여준다" {
            val quizSet = saveCurrentWeekQuizSet()
            val tester = saveMember("테스터")
            val requestedDummy = saveMember("dummy-female-aaaa")
            val idleDummy = saveMember("dummy-male-bbbb")
            saveRequest(requester = tester, receiver = requestedDummy, quizSet = quizSet)
            saveCandidatePair(quizSet, requestedDummy, tester)
            saveCandidatePair(quizSet, idleDummy, tester)
            saveCandidatePair(quizSet, idleDummy, requestedDummy)

            val console = mockMvc.perform(get("/admin/qa").with(authentication(admin)))
                .andExpect(status().isOk)
                .andReturn().modelAndView.shouldNotBeNull().model["console"] as QaConsoleView

            console.dummyCount shouldBe 2
            console.personal.receivedRequests.map { it.dummy.id to it.requester.id } shouldBe
                listOf(requestedDummy.id to tester.id)
            console.personal.requestOptions.map { it.dummy.id to it.receiver.id } shouldBe
                listOf(idleDummy.id to tester.id)
        }
    }

    "1:1 대신 응답" - {
        "더미가 받은 신청을 수락하면 성사되고 1:1 채팅방이 생긴다" {
            val quizSet = saveCurrentWeekQuizSet()
            val tester = saveMember("테스터")
            val dummy = saveMember("dummy-female-aaaa")
            val match = saveRequest(requester = tester, receiver = dummy, quizSet = quizSet)

            acceptAs(dummy.id, match.id)
                .andExpect(redirectedUrl("/admin/qa#personal"))
                .andExpect(flash().attributeExists("message"))

            personalMatchRepository.findByIdOrNull(match.id)?.status shouldBe PersonalMatchStatus.ACCEPTED
            chatRoomRepository.findBySourceTypeAndSourceId(ChatRoomType.PERSONAL, match.id).shouldNotBeNull()
        }

        "더미가 받은 신청을 거절한다" {
            val quizSet = saveCurrentWeekQuizSet()
            val tester = saveMember("테스터")
            val dummy = saveMember("dummy-female-aaaa")
            val match = saveRequest(requester = tester, receiver = dummy, quizSet = quizSet)

            rejectAs(dummy.id, match.id)
                .andExpect(flash().attributeExists("message"))

            personalMatchRepository.findByIdOrNull(match.id)?.status shouldBe PersonalMatchStatus.REJECTED
        }

        "더미가 실회원에게 1:1 신청을 보낸다" {
            val quizSet = saveCurrentWeekQuizSet()
            val tester = saveMember("테스터")
            val dummy = saveMember("dummy-female-aaaa")

            mockMvc.perform(
                post("/admin/qa/dummies/{dummyId}/personal-matches", dummy.id)
                    .param("receiverId", tester.id.toString())
                    .param("quizSetId", quizSet.id.toString())
                    .asAdmin(),
            ).andExpect(flash().attributeExists("message"))

            val sent = personalMatchRepository.findByRequesterIdAndQuizSetId(dummy.id, quizSet.id).single()
            sent.receiverId() shouldBe tester.id
            sent.status shouldBe PersonalMatchStatus.PENDING
        }

        "실회원으로는 움직이지 않는다" {
            val quizSet = saveCurrentWeekQuizSet()
            val dummy = saveMember("dummy-female-aaaa")
            val realReceiver = saveMember("실회원")
            val match = saveRequest(requester = dummy, receiver = realReceiver, quizSet = quizSet)

            acceptAs(realReceiver.id, match.id)
                .andExpect(flash().attribute("error", containsString("더미 회원만")))

            personalMatchRepository.findByIdOrNull(match.id)?.status shouldBe PersonalMatchStatus.PENDING
        }

        "앱이 받는 거부는 오류 코드와 함께 보여준다" {
            val pastQuizSet = quizSetRepository.save(QuizSetFixture.create())
            val tester = saveMember("테스터")
            val dummy = saveMember("dummy-female-aaaa")
            val match = saveRequest(requester = tester, receiver = dummy, quizSet = pastQuizSet)

            acceptAs(dummy.id, match.id)
                .andExpect(flash().attribute("error", containsString("(5008)")))
        }
    }
})
