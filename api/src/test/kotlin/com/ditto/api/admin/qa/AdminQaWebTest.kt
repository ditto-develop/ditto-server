package com.ditto.api.admin.qa

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.api.admin.qa.dto.QaConsoleView
import com.ditto.api.admin.qa.dto.QaRoomSummary
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.match.MatchCandidateFixture
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.entity.GroupMatch
import com.ditto.domain.match.entity.GroupMatchMember
import com.ditto.domain.match.entity.InvitationStatus
import com.ditto.domain.match.entity.PersonalMatch
import com.ditto.domain.match.entity.PersonalMatchStatus
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QuizSet
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.system.OperationWeek
import com.ditto.domain.system.entity.ServerTimeOverride
import com.ditto.domain.system.repository.ServerTimeOverrideRepository
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource

@AutoConfigureMockMvc
class AdminQaWebTest(
    private val mockMvc: MockMvc,
    private val memberRepository: MemberRepository,
    private val quizSetRepository: QuizSetRepository,
    private val personalMatchRepository: PersonalMatchRepository,
    private val matchCandidateRepository: MatchCandidateRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val groupMatchRepository: GroupMatchRepository,
    private val groupMatchMemberRepository: GroupMatchMemberRepository,
    private val serverTimeOverrideRepository: ServerTimeOverrideRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val admin = UsernamePasswordAuthenticationToken(
        AdminPrincipal(1L, "관리자", "admin@ditto.pics"),
        null,
        listOf(SimpleGrantedAuthority("ROLE_ADMIN")),
    )

    val thisMonday = OperationWeek.containing(LocalDate.now()).startedOn

    fun overrideServerTime(at: LocalDateTime) {
        serverTimeOverrideRepository.deleteAll()
        serverTimeOverrideRepository.save(
            ServerTimeOverride.disabled().apply { override(at, "관리자", "admin@ditto.pics") },
        )
    }

    // 그룹 응답은 금요일 00:00에 마감된다. 어느 요일에 돌려도 같게 이번 주 수요일로 둔다.
    beforeEach { overrideServerTime(thisMonday.plusDays(2).atTime(12, 0)) }

    fun MockHttpServletRequestBuilder.asAdmin() = with(authentication(admin)).with(csrf())

    fun console(): QaConsoleView =
        mockMvc.perform(get("/admin/qa").with(authentication(admin)))
            .andExpect(status().isOk)
            .andReturn().modelAndView.shouldNotBeNull().model["console"] as QaConsoleView

    fun saveCurrentWeekQuizSet(matchingType: MatchingType = MatchingType.ONE_TO_ONE): QuizSet =
        quizSetRepository.save(QuizSetFixture.currentWeek(matchingType = matchingType))

    fun saveGroup(quizSet: QuizSet, members: List<Member>): GroupMatch {
        val group = groupMatchRepository.save(GroupMatch.candidate(quizSetId = quizSet.id, score = 80.0))
        groupMatchMemberRepository.saveAll(members.map { GroupMatchMember.candidate(group.id, it.id) })
        return group
    }

    fun invitationStatusOf(group: GroupMatch, member: Member): InvitationStatus? =
        groupMatchMemberRepository.findByRoomIdAndMemberId(group.id, member.id)?.status

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

            val console = console()

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
                .andExpect(flash().attribute("message", "dummy-female-aaaa(#${dummy.id}) · 1:1 신청 #${match.id} 수락 완료"))

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
            ).andExpect(redirectedUrl("/admin/qa#personal-sent"))
                .andExpect(flash().attributeExists("message"))

            val sent = personalMatchRepository.findByRequesterIdAndQuizSetId(dummy.id, quizSet.id).single()
            sent.receiverId() shouldBe tester.id
            sent.status shouldBe PersonalMatchStatus.PENDING
            console().personal.sentRequests.single().let {
                it.matchId shouldBe sent.id
                it.receiver.id shouldBe tester.id
                it.status shouldBe PersonalMatchStatus.PENDING
            }
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
                .andExpect(flash().attribute("error", containsString("(코드 5008)")))
        }
    }

    "그룹 대신 응답" - {
        val groupMatchUrl = "/admin/qa/dummies/{dummyId}/group-matches/{groupMatchId}"

        "콘솔은 더미가 있는 그룹만 보여주고 실회원을 위에 둔다" {
            val quizSet = saveCurrentWeekQuizSet(MatchingType.GROUP)
            val tester = saveMember("테스터")
            val firstDummy = saveMember("dummy-male-aaaa")
            val secondDummy = saveMember("dummy-female-bbbb")
            val groupWithDummies = saveGroup(quizSet, listOf(secondDummy, tester, firstDummy))
            saveGroup(quizSet, listOf(saveMember("실회원2"), saveMember("실회원3"), saveMember("실회원4")))

            val group = console().group.groups.single()

            group.groupMatchId shouldBe groupWithDummies.id
            group.members.map { it.member.id } shouldBe listOf(tester.id, firstDummy.id, secondDummy.id)
            group.hasPendingDummy shouldBe true
        }

        "그룹 구성원 닉네임은 회원별 퀴즈 현황으로 이어진다" {
            val quizSet = saveCurrentWeekQuizSet(MatchingType.GROUP)
            val tester = saveMember("테스터")
            saveGroup(quizSet, listOf(tester, saveMember("dummy-male-aaaa"), saveMember("dummy-female-bbbb")))

            mockMvc.perform(get("/admin/qa").with(authentication(admin)))
                .andExpect(content().string(containsString("/admin/members/${tester.id}/quizzes")))
        }

        "더미가 그룹 초대를 수락한다" {
            val quizSet = saveCurrentWeekQuizSet(MatchingType.GROUP)
            val dummy = saveMember("dummy-male-aaaa")
            val group = saveGroup(quizSet, listOf(saveMember("테스터"), dummy, saveMember("dummy-female-bbbb")))

            mockMvc.perform(post("$groupMatchUrl/accept", dummy.id, group.id).asAdmin())
                .andExpect(redirectedUrl("/admin/qa#group"))
                .andExpect(flash().attributeExists("message"))

            invitationStatusOf(group, dummy) shouldBe InvitationStatus.ACCEPTED
            groupMatchRepository.findByIdOrNull(group.id)?.acceptedCount shouldBe 1
        }

        "더미가 그룹 초대를 거절한다" {
            val quizSet = saveCurrentWeekQuizSet(MatchingType.GROUP)
            val dummy = saveMember("dummy-male-aaaa")
            val group = saveGroup(quizSet, listOf(saveMember("테스터"), dummy, saveMember("dummy-female-bbbb")))

            mockMvc.perform(post("$groupMatchUrl/decline", dummy.id, group.id).asAdmin())
                .andExpect(flash().attributeExists("message"))

            invitationStatusOf(group, dummy) shouldBe InvitationStatus.DECLINED
        }

        "대기 중인 더미를 모두 수락하면 그룹이 성사되고 채팅방이 생긴다" {
            val quizSet = saveCurrentWeekQuizSet(MatchingType.GROUP)
            val tester = saveMember("테스터")
            val dummies = listOf("dummy-male-aaaa", "dummy-female-bbbb", "dummy-male-cccc").map { saveMember(it) }
            val group = saveGroup(quizSet, dummies + tester)

            mockMvc.perform(post("/admin/qa/group-matches/{id}/accept-pending-dummies", group.id).asAdmin())
                .andExpect(flash().attributeExists("message"))

            dummies.map { invitationStatusOf(group, it) } shouldBe List(3) { InvitationStatus.ACCEPTED }
            invitationStatusOf(group, tester) shouldBe InvitationStatus.PENDING
            groupMatchRepository.findByIdOrNull(group.id)?.isActive shouldBe true
            chatRoomRepository.findBySourceTypeAndSourceId(ChatRoomType.GROUP, group.id).shouldNotBeNull()
        }

        "실회원이 든 그룹이 위에 오고, 성사된 그룹은 열린 방으로 이어진다" {
            val quizSet = saveCurrentWeekQuizSet(MatchingType.GROUP)
            val tester = saveMember("테스터")
            val dummies = listOf("dummy-male-aaaa", "dummy-female-bbbb", "dummy-male-cccc").map { saveMember(it) }
            val testerGroup = saveGroup(quizSet, dummies + tester)
            val dummyOnlyGroup = saveGroup(quizSet, listOf("dummy-a", "dummy-b", "dummy-c").map { saveMember(it) })
            mockMvc.perform(post("/admin/qa/group-matches/{id}/accept-pending-dummies", testerGroup.id).asAdmin())

            val groups = console().group.groups

            groups.map { it.groupMatchId } shouldBe listOf(testerGroup.id, dummyOnlyGroup.id)
            groups.first().chatRoomId shouldBe
                chatRoomRepository.findBySourceTypeAndSourceId(ChatRoomType.GROUP, testerGroup.id)?.id
            groups.last().chatRoomId shouldBe null

            @Suppress("UNCHECKED_CAST")
            val rooms = mockMvc.perform(get("/admin/qa").with(authentication(admin)))
                .andReturn().modelAndView.shouldNotBeNull().model["rooms"] as List<QaRoomSummary>
            rooms.single().sourceLabel shouldBe "그룹 #${testerGroup.id} · 이번 주 퀴즈"
            rooms.single().realMembers shouldBe emptyList()
            rooms.single().dummyCount shouldBe 3
        }

        "응답 마감이 지나면 남은 초대가 있을 때 마감 시각과 코드를 알려준다" {
            val quizSet = saveCurrentWeekQuizSet(MatchingType.GROUP)
            saveGroup(quizSet, listOf("테스터", "dummy-male-aaaa", "dummy-female-bbbb").map { saveMember(it) })
            console().group.isResponseClosed shouldBe false

            overrideServerTime(thisMonday.plusDays(4).atStartOfDay())

            console().group.isResponseClosed shouldBe true
            mockMvc.perform(get("/admin/qa").with(authentication(admin)))
                .andExpect(content().string(containsString("이번 주 그룹 응답 마감(")))
                .andExpect(content().string(containsString("(코드 5008)")))
        }

        "대기 중인 더미가 없으면 알려준다" {
            val quizSet = saveCurrentWeekQuizSet(MatchingType.GROUP)
            val group = saveGroup(quizSet, listOf(saveMember("테스터"), saveMember("실회원2"), saveMember("실회원3")))

            mockMvc.perform(post("/admin/qa/group-matches/{id}/accept-pending-dummies", group.id).asAdmin())
                .andExpect(flash().attribute("error", containsString("대상 더미가 없습니다")))
        }
    }

    "시각 바로가기" - {
        "알림 바로가기는 알림 설정의 리드 시간으로 시각을 잡는다" {
            val shortcuts = console().timeline.shortcuts.associate { it.label to it.dateTime }

            shortcuts["첫 인사 알림 직후"] shouldBe thisMonday.plusDays(4).atTime(12, 1)
            shortcuts["마감 임박 알림 직후"] shouldBe thisMonday.plusDays(6).atTime(18, 1)
        }

        "콘솔 화면에 타임라인 바로가기와 상대 이동 버튼을 그린다" {
            mockMvc.perform(get("/admin/qa").with(authentication(admin)))
                .andExpect(content().string(containsString("첫 인사 알림 직후")))
                .andExpect(content().string(containsString("대화 없는 방만, 방마다 한 번")))
                .andExpect(content().string(containsString("+1시간 · ")))
        }

        "서버 시각을 옮기고 보던 화면으로 돌아간다" {
            val friday = thisMonday.plusDays(4).atTime(0, 1)

            mockMvc.perform(
                post("/admin/qa/server-time")
                    .param("dateTime", friday.toString())
                    .param("returnTo", "/admin/qa/rooms/7")
                    .asAdmin(),
            ).andExpect(redirectedUrl("/admin/qa/rooms/7"))

            console().now shouldBe friday
        }

        "콘솔 밖 주소로는 돌려보내지 않는다" {
            val outsideConsole =
                listOf("//evil.example.com", "/admin/qa//evil.example.com", "/admin/qaXYZ", "/admin/members")
            outsideConsole.forEach { returnTo ->
                mockMvc.perform(
                    post("/admin/qa/server-time")
                        .param("dateTime", thisMonday.atTime(9, 0).toString())
                        .param("returnTo", returnTo)
                        .asAdmin(),
                ).andExpect(redirectedUrl("/admin/qa"))
            }
        }

        "실제 시각으로 되돌린다" {
            mockMvc.perform(post("/admin/qa/server-time/disable").asAdmin())
                .andExpect(redirectedUrl("/admin/qa"))

            serverTimeOverrideRepository.findAll().single().enabled shouldBe false
        }
    }
})
