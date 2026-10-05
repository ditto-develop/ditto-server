package com.ditto.api.admin

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.api.match.matching.MatchScore
import com.ditto.api.match.matching.ScoredMatch
import com.ditto.api.match.service.CandidateGenerationSummary
import com.ditto.api.match.service.CandidateRowCounts
import com.ditto.api.support.JunitDatabaseCleanExtension
import com.ditto.domain.match.GroupMatchFixture
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.entity.GroupMatchMember
import com.ditto.domain.match.entity.MatchCandidate
import com.ditto.domain.match.repository.GroupMatchMemberRepository
import com.ditto.domain.match.repository.GroupMatchRepository
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.Interest
import com.ditto.domain.member.entity.Job
import com.ditto.domain.member.entity.Location
import com.ditto.domain.member.entity.MemberRole
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.memberreport.MemberReportFixture
import com.ditto.domain.memberreport.repository.MemberReportRepository
import com.ditto.domain.notification.entity.NotificationType
import com.ditto.domain.notification.repository.NotificationRepository
import com.ditto.domain.notification.repository.SystemNoticeRepository
import com.ditto.domain.quiz.QuizAnswerFixture
import com.ditto.domain.quiz.QuizChoiceFixture
import com.ditto.domain.quiz.QuizFixture
import com.ditto.domain.quiz.QuizProgressFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.repository.QuizAnswerRepository
import com.ditto.domain.quiz.repository.QuizChoiceRepository
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import com.ditto.domain.socialaccount.entity.SocialAccount
import com.ditto.domain.socialaccount.entity.SocialProvider
import com.ditto.domain.socialaccount.repository.SocialAccountRepository
import com.ditto.infrastructure.oauth.apple.AppleNativeFakeAuthenticator
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

// 같은 컨텍스트를 쓰는 IntegrationTest(AdminQaWebTest 등)가 커밋한 행이 남아 있을 수 있어 시작 전에 비운다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local", "test")
@ExtendWith(JunitDatabaseCleanExtension::class)
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
    lateinit var quizProgressRepository: QuizProgressRepository

    @Autowired
    lateinit var quizAnswerRepository: QuizAnswerRepository

    @Autowired
    lateinit var memberRepository: MemberRepository

    @Autowired
    lateinit var socialAccountRepository: SocialAccountRepository

    @Autowired
    lateinit var memberReportRepository: MemberReportRepository

    @Autowired
    lateinit var groupMatchRepository: GroupMatchRepository

    @Autowired
    lateinit var groupMatchMemberRepository: GroupMatchMemberRepository

    @Autowired
    lateinit var matchCandidateRepository: MatchCandidateRepository

    @Autowired
    lateinit var personalMatchRepository: PersonalMatchRepository

    @Autowired
    lateinit var systemNoticeRepository: SystemNoticeRepository

    @Autowired
    lateinit var notificationRepository: NotificationRepository

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
        mockMvc.perform(get("/admin/notices").with(authentication(admin()))).andExpect(status().isOk)
    }

    @Test
    @DisplayName("시스템 공지를 보내면 활성 회원에게 알림이 남고 이력에 기록된다")
    fun publishSystemNotice() {
        val active = memberRepository.save(MemberFixture.create(nickname = "활성", email = "active@ditto.pics", status = MemberStatus.ACTIVE))
        memberRepository.save(MemberFixture.create(nickname = "탈퇴", email = "left@ditto.pics", status = MemberStatus.LEFT))

        mockMvc.perform(
            post("/admin/notices").with(authentication(admin())).with(csrf())
                .param("title", "ditto가 업데이트됐어요").param("body", "이번에 달라진 점을 확인해보세요."),
        ).andExpect(status().is3xxRedirection)

        val notice = systemNoticeRepository.findAllByOrderByIdDesc().single()
        notice.targetCount shouldBe 1
        notice.recipientCount shouldBe 1
        notice.isSending shouldBe false
        notice.authorMemberId shouldBe 1L
        notificationRepository.findAll().single().let {
            it.memberId shouldBe active.id
            it.type shouldBe NotificationType.SYSTEM_NOTICE
            it.targetId shouldBe notice.id
        }
    }

    @Test
    @DisplayName("제목이 비면 공지를 보내지 않고 오류 메시지와 입력값을 돌려준다")
    fun publishSystemNoticeRejectsBlankTitle() {
        mockMvc.perform(
            post("/admin/notices").with(authentication(admin())).with(csrf())
                .param("title", "   ").param("body", "쓰던 본문"),
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(flash().attributeExists("error"))
            .andExpect(flash().attribute("body", "쓰던 본문"))

        systemNoticeRepository.count() shouldBe 0
    }

    @Test
    @DisplayName("퀴즈셋을 문항과 함께 생성하고 상세·수정 화면을 조회한다")
    fun createWithQuizzesAndDetail() {
        mockMvc.perform(
            post("/admin/quiz-sets")
                .with(authentication(admin())).with(csrf())
                .param("category", "성격").param("title", "테스트 퀴즈셋")
                .param("description", "설명")
                .param("weekStartedOn", "2026-06-15")
                .param("matchingType", "ONE_TO_ONE").param("isActive", "true")
                .param("quizzes[0].question", "치약 짤 때?")
                .param("quizzes[0].choices[0].content", "아래부터")
                .param("quizzes[0].choices[1].content", "중간부터")
                .param("quizzes[1].question", "여행 계획은?")
                .param("quizzes[1].choices[0].content", "분 단위로")
                .param("quizzes[1].choices[1].content", "즉흥적으로"),
        ).andExpect(status().is3xxRedirection)

        val created = quizSetRepository.findAllByOrderByWeekStartedOnDescIdDesc().first { it.title == "테스트 퀴즈셋" }
        val quizzes = quizRepository.findByQuizSetIdOrderByDisplayOrderAsc(created.id)
        quizzes.map { it.displayOrder } shouldBe listOf(1, 2)
        quizzes.map { it.question } shouldBe listOf("치약 짤 때?", "여행 계획은?")
        quizChoiceRepository.findByQuizIdOrderByDisplayOrderAsc(quizzes[0].id)
            .map { it.displayOrder } shouldBe listOf(1, 2)

        mockMvc.perform(get("/admin/quiz-sets/{id}", created.id).with(authentication(admin())))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("/admin/quiz-sets/${created.id}/delete")))
        mockMvc.perform(get("/admin/quiz-sets/{id}/edit", created.id).with(authentication(admin())))
            .andExpect(status().isOk)
    }

    @Test
    @DisplayName("1:1 퀴즈셋 참여 현황은 저장된 후보·점수·신청 상태와 후보가 없는 이유를 그린다")
    fun quizSetParticipantsPersonalMatchingColumn() {
        val quizSet = quizSetRepository.save(QuizSetFixture.create(matchingType = MatchingType.ONE_TO_ONE))
        val requester = saveCompletedMember(quizSet.id, "신청한회원")
        val receiver = saveCompletedMember(quizSet.id, "dummy-female-0001")
        matchCandidateRepository.save(MatchCandidate.create(requester, receiver, quizSet.id, 66.7, 2, 3))
        matchCandidateRepository.save(MatchCandidate.create(receiver, requester, quizSet.id, 66.7, 2, 3))
        personalMatchRepository.save(PersonalMatchFixture.create(requesterId = requester, receiverId = receiver, quizSetId = quizSet.id))
        val notCompleted = memberRepository.save(MemberFixture.create(nickname = "미완주회원", status = MemberStatus.ACTIVE)).id
        quizProgressRepository.save(QuizProgressFixture.create(memberId = notCompleted, quizSetId = quizSet.id, totalCount = 1))

        mockMvc.perform(get("/admin/quiz-sets/{id}/participants", quizSet.id).with(authentication(admin())))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("매칭 열: 후보 생성")))
            .andExpect(content().string(containsString("dummy-female-0001 (#$receiver)")))
            .andExpect(content().string(containsString("/admin/quiz-sets/${quizSet.id}/participants?q=%23$receiver")))
            .andExpect(content().string(containsString("66.7 (2/3)")))
            .andExpect(content().string(containsString("<span class=\"badge matching\">신청함</span>")))
            .andExpect(content().string(containsString("<span class=\"badge matching\">신청 받음</span>")))
            .andExpect(content().string(containsString("<span class=\"muted\">미완주</span>")))
            .andExpect(content().string(containsString("→ 퀴즈를 끝까지 풀기")))
    }

    @Test
    @DisplayName("그룹 퀴즈셋 참여 현황은 그룹 점수·성사 여부·구성원 응답을 그린다")
    fun quizSetParticipantsGroupMatchingColumn() {
        val quizSet = quizSetRepository.save(QuizSetFixture.create(matchingType = MatchingType.GROUP))
        val members = listOf("그룹원A", "그룹원B", "그룹원C").map { saveCompletedMember(quizSet.id, it) }
        val group = groupMatchRepository.save(GroupMatchFixture.create(quizSetId = quizSet.id, score = 75.0, acceptedCount = 1))
        groupMatchMemberRepository.save(GroupMatchMember.candidate(group.id, members[0]).also { it.accept() })
        members.drop(1).forEach { groupMatchMemberRepository.save(GroupMatchMember.candidate(group.id, it)) }

        mockMvc.perform(get("/admin/quiz-sets/{id}/participants", quizSet.id).with(authentication(admin())))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("그룹 #${group.id} · 75.0 · 수락 1/3")))
            .andExpect(content().string(containsString("<span class=\"badge on\">수락</span>")))
            .andExpect(content().string(containsString("<span class=\"badge matching\">대기</span>")))
    }

    @Test
    @DisplayName("퀴즈셋 참여 현황은 참여자 찾기 칸을 두고 행마다 회원 ID·닉네임을 싣는다")
    fun quizSetParticipantsSearchAttributes() {
        val quizSet = quizSetRepository.save(QuizSetFixture.create())
        val memberId = saveCompletedMember(quizSet.id, "찾을회원")
        val deletedMemberId = 99999L
        quizProgressRepository.save(QuizProgressFixture.create(memberId = deletedMemberId, quizSetId = quizSet.id, totalCount = 1))

        mockMvc.perform(get("/admin/quiz-sets/{id}/participants", quizSet.id).with(authentication(admin())))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("id=\"participantSearch\"")))
            .andExpect(content().string(containsString("/admin/js/participant-search.js")))
            .andExpect(content().string(containsString("data-member-id=\"$memberId\"")))
            .andExpect(content().string(containsString("data-nickname=\"찾을회원\"")))
            .andExpect(content().string(containsString("data-member-id=\"$deletedMemberId\" class=\"participant-deleted\">")))
    }

    @Test
    @DisplayName("퀴즈셋 참여 현황은 ACTIVE가 아닌 회원 상태만 한글 배지로 보여 준다")
    fun quizSetParticipantsInactiveMemberStatusBadge() {
        val quizSet = quizSetRepository.save(QuizSetFixture.create())
        saveCompletedMember(quizSet.id, "활동회원")
        val leftMemberId = memberRepository.save(MemberFixture.create(nickname = "탈퇴회원", status = MemberStatus.LEFT)).id
        quizProgressRepository.save(QuizProgressFixture.create(memberId = leftMemberId, quizSetId = quizSet.id, totalCount = 1))

        mockMvc.perform(get("/admin/quiz-sets/{id}/participants", quizSet.id).with(authentication(admin())))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("<span class=\"badge matching\">탈퇴</span>")))
            .andExpect(content().string(not(containsString(">ACTIVE<"))))
    }

    private fun saveCompletedMember(quizSetId: Long, nickname: String): Long {
        val memberId = memberRepository.save(MemberFixture.create(nickname = nickname, status = MemberStatus.ACTIVE)).id
        val progress = QuizProgressFixture.create(memberId = memberId, quizSetId = quizSetId, totalCount = 1)
        progress.recordAnswer()
        quizProgressRepository.save(progress)
        return memberId
    }

    @Test
    @DisplayName("퀴즈셋 참여 현황은 실회원·더미·삭제된 회원의 진행·프로필·고른 선택지를 그린다")
    fun quizSetParticipantsPage() {
        val quizSet = quizSetRepository.save(QuizSetFixture.create())
        val firstQuiz = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id, question = "치약 짤 때?", displayOrder = 1))
        val secondQuiz = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id, question = "여행 계획은?", displayOrder = 2))
        quizChoiceRepository.save(QuizChoiceFixture.create(quizId = firstQuiz.id, content = "아래부터", displayOrder = 1))
        val firstPicked = quizChoiceRepository.save(QuizChoiceFixture.create(quizId = firstQuiz.id, content = "중간부터", displayOrder = 2))
        val secondPicked = quizChoiceRepository.save(QuizChoiceFixture.create(quizId = secondQuiz.id, content = "즉흥적으로", displayOrder = 1))
        quizChoiceRepository.save(QuizChoiceFixture.create(quizId = secondQuiz.id, content = "분 단위로", displayOrder = 2))

        val dummy = memberRepository.save(
            MemberFixture.create(
                nickname = "dummy-female-1a2b",
                status = MemberStatus.ACTIVE,
                interests = setOf(Interest.MUSIC, Interest.TRAVEL),
                location = Location.SEOUL,
                job = Job.DESIGN,
                caricature = "/onboarding/profileimg/avatar/f3.svg",
            ),
        )
        val completed = QuizProgressFixture.create(memberId = dummy.id, quizSetId = quizSet.id, totalCount = 2)
        repeat(2) { completed.recordAnswer() }
        quizProgressRepository.save(completed)
        quizAnswerRepository.save(QuizAnswerFixture.create(memberId = dummy.id, quizId = firstQuiz.id, choiceId = firstPicked.id))
        quizAnswerRepository.save(QuizAnswerFixture.create(memberId = dummy.id, quizId = secondQuiz.id, choiceId = secondPicked.id))

        val real = memberRepository.save(
            MemberFixture.create(nickname = "실회원테스터", status = MemberStatus.ACTIVE, gender = Gender.FEMALE),
        )
        val inProgress = QuizProgressFixture.create(memberId = real.id, quizSetId = quizSet.id, totalCount = 2)
        inProgress.recordAnswer()
        quizProgressRepository.save(inProgress)
        quizAnswerRepository.save(QuizAnswerFixture.create(memberId = real.id, quizId = firstQuiz.id, choiceId = firstPicked.id))

        quizProgressRepository.save(QuizProgressFixture.create(memberId = 99999L, quizSetId = quizSet.id, totalCount = 2))

        // 요약 카드 라벨에도 같은 글자가 있어 배지는 마크업까지 넣어 확인한다.
        mockMvc.perform(get("/admin/quiz-sets/{id}/participants", quizSet.id).with(authentication(admin())))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("dummy-female-1a2b")))
            .andExpect(content().string(containsString(">즉흥적으로<")))
            .andExpect(content().string(containsString(">f3<")))
            .andExpect(content().string(containsString("실회원테스터")))
            .andExpect(content().string(containsString("<span class=\"badge cat\">실회원</span>")))
            .andExpect(content().string(containsString("<span class=\"badge on\">완료</span>")))
            .andExpect(content().string(containsString("<span class=\"badge matching\">진행 중</span>")))
            .andExpect(content().string(containsString("<span class=\"badge off\">시작 전</span>")))
            .andExpect(content().string(containsString("<span class=\"badge off\">삭제된 회원</span>")))
            .andExpect(content().string(containsString(">여성<")))
            .andExpect(content().string(containsString(">서울<")))
            .andExpect(content().string(containsString(">디자인<")))
            .andExpect(content().string(containsString("음악")))
            .andExpect(content().string(containsString("<li>여행 계획은?</li>")))
    }

    @Test
    @DisplayName("이번 주 매칭이 끝난 퀴즈셋 상세는 QA 도구 카드에 미리보기와 세 버튼을 보여 준다")
    fun quizSetDetailShowsQaTools() {
        val quizSet = quizSetRepository.save(QuizSetFixture.currentWeek())
        personalMatchRepository.save(PersonalMatchFixture.create(1L, 2L, quizSet.id))
        quizProgressRepository.save(QuizProgressFixture.create(memberId = 1L, quizSetId = quizSet.id))

        mockMvc.perform(get("/admin/quiz-sets/{id}", quizSet.id).with(authentication(admin())))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("href=\"#qa-tools\"")))
            .andExpect(content().string(containsString("세 도구가 모두 지우는 매칭 기록: 1:1 신청 1건")))
            .andExpect(content().string(containsString("참여 회원 1명의 답·진행을 지웁니다.")))
            .andExpect(content().string(containsString(">매칭 기록 초기화</button>")))
            .andExpect(content().string(containsString(">전체 답·진행 초기화</button>")))
            .andExpect(content().string(containsString(">퀴즈셋 강제 삭제</button>")))
    }

    @Test
    @DisplayName("매칭 전이어도 참여자가 있으면 QA 도구 카드에 답·진행 초기화만 보이고 실회원 수를 알린다")
    fun quizSetDetailBeforeMatchingShowsAnswerReset() {
        val quizSet = quizSetRepository.save(QuizSetFixture.currentWeek())
        saveCompletedMember(quizSet.id, "실회원참여자")
        saveCompletedMember(quizSet.id, "dummy-male-0001")

        mockMvc.perform(get("/admin/quiz-sets/{id}", quizSet.id).with(authentication(admin())))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("id=\"qa-tools\"")))
            .andExpect(content().string(containsString("참여 회원 2명(실회원 1명)의 답·진행을 지웁니다.")))
            .andExpect(content().string(containsString("전체 답·진행 초기화는 실회원 1명의 답도 지웁니다.")))
            .andExpect(content().string(containsString("data-confirm=\"참여자 2명(실회원 1명)의 답·진행을 지웁니다. 퀴즈셋·문항은 남습니다.")))
            .andExpect(content().string(containsString("/admin/quiz-sets/${quizSet.id}/participants\">참여 현황</a>에서 회원별로 초기화하세요.")))
            .andExpect(content().string(containsString(">전체 답·진행 초기화</button>")))
            .andExpect(content().string(not(containsString(">매칭 기록 초기화</button>"))))
            .andExpect(content().string(not(containsString(">퀴즈셋 강제 삭제</button>"))))
    }

    @Test
    @DisplayName("지난 주 퀴즈셋 상세는 초기화 버튼 대신 강제 삭제 안내를 보여 준다")
    fun pastWeekQuizSetDetailHidesReset() {
        val quizSet = quizSetRepository.save(QuizSetFixture.create())
        personalMatchRepository.save(PersonalMatchFixture.create(1L, 2L, quizSet.id))

        mockMvc.perform(get("/admin/quiz-sets/{id}", quizSet.id).with(authentication(admin())))
            .andExpect(status().isOk)
            .andExpect(content().string(not(containsString(">매칭 기록 초기화</button>"))))
            .andExpect(content().string(not(containsString("서버 시각을 그 주 목요일"))))
            .andExpect(content().string(not(containsString("아래 안내대로 시간 오버라이드를 쓰면"))))
            .andExpect(content().string(containsString("이번 주 퀴즈셋만 초기화할 수 있습니다.")))
            .andExpect(content().string(containsString(">퀴즈셋 강제 삭제</button>")))
    }

    @Test
    @DisplayName("참여자도 매칭 기록도 없는 퀴즈셋 상세에는 QA 도구 카드가 없다")
    fun quizSetWithoutMatchingHasNoQaTools() {
        val quizSet = quizSetRepository.save(QuizSetFixture.currentWeek())

        mockMvc.perform(get("/admin/quiz-sets/{id}", quizSet.id).with(authentication(admin())))
            .andExpect(status().isOk)
            .andExpect(content().string(not(containsString("id=\"qa-tools\""))))
    }

    @Test
    @DisplayName("매칭 기록 초기화는 매칭 기록만 지우고 상세로 돌아간다")
    fun resetQuizSetMatching() {
        val quizSet = quizSetRepository.save(QuizSetFixture.currentWeek())
        personalMatchRepository.save(PersonalMatchFixture.create(1L, 2L, quizSet.id))

        mockMvc.perform(post("/admin/quiz-sets/{id}/qa/reset-matching", quizSet.id).with(authentication(admin())).with(csrf()))
            .andExpect(redirectedUrl("/admin/quiz-sets/${quizSet.id}"))
            .andExpect(flash().attributeExists("message"))

        personalMatchRepository.existsByQuizSetId(quizSet.id) shouldBe false
        quizSetRepository.existsById(quizSet.id) shouldBe true
    }

    @Test
    @DisplayName("전체 답·진행 초기화는 매칭 기록과 답·진행을 지우고 퀴즈셋은 남긴 채 상세로 돌아간다")
    fun resetQuizSetAnswers() {
        val quizSet = quizSetRepository.save(QuizSetFixture.currentWeek())
        val memberId = saveCompletedMember(quizSet.id, "dummy-male-0001")
        personalMatchRepository.save(PersonalMatchFixture.create(memberId, 2L, quizSet.id))

        mockMvc.perform(post("/admin/quiz-sets/{id}/qa/reset-answers", quizSet.id).with(authentication(admin())).with(csrf()))
            .andExpect(redirectedUrl("/admin/quiz-sets/${quizSet.id}"))
            .andExpect(flash().attribute("message", containsString("답·진행을 초기화했습니다. 참여자 1명")))
            .andExpect(flash().attribute("message", containsString("다시 하려면 퀴즈 기간(월~수)에 다시 풀고")))

        quizProgressRepository.findByMemberIdAndQuizSetId(memberId, quizSet.id) shouldBe null
        personalMatchRepository.existsByQuizSetId(quizSet.id) shouldBe false
        quizSetRepository.existsById(quizSet.id) shouldBe true
    }

    @Test
    @DisplayName("이번 주 매칭 전 퀴즈셋 참여 현황은 행마다 답·진행 초기화 버튼을 보여 준다")
    fun quizSetParticipantsShowsMemberReset() {
        val quizSet = quizSetRepository.save(QuizSetFixture.currentWeek())
        saveCompletedMember(quizSet.id, "dummy-male-0001")

        mockMvc.perform(get("/admin/quiz-sets/{id}/participants", quizSet.id).with(authentication(admin())))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString(">답·진행 초기화</button>")))
    }

    @Test
    @DisplayName("매칭이 진행된 퀴즈셋 참여 현황은 행별 초기화 버튼 대신 상세 QA 도구로 안내한다")
    fun matchedQuizSetParticipantsHidesMemberReset() {
        val quizSet = quizSetRepository.save(QuizSetFixture.currentWeek())
        val memberId = saveCompletedMember(quizSet.id, "dummy-male-0001")
        personalMatchRepository.save(PersonalMatchFixture.create(memberId, 2L, quizSet.id))

        mockMvc.perform(get("/admin/quiz-sets/{id}/participants", quizSet.id).with(authentication(admin())))
            .andExpect(status().isOk)
            .andExpect(content().string(not(containsString(">답·진행 초기화</button>"))))
            .andExpect(content().string(containsString("매칭이 진행된 퀴즈셋은 회원별로 초기화할 수 없습니다.")))
            .andExpect(content().string(containsString("/admin/quiz-sets/${quizSet.id}#qa-tools")))
    }

    @Test
    @DisplayName("회원별 답·진행 초기화는 그 회원의 답·진행만 지우고 참여 현황으로 돌아간다")
    fun resetMemberQuizAnswers() {
        val quizSet = quizSetRepository.save(QuizSetFixture.currentWeek())
        val target = saveCompletedMember(quizSet.id, "dummy-male-0001")
        val other = saveCompletedMember(quizSet.id, "dummy-male-0002")

        mockMvc.perform(
            post("/admin/quiz-sets/{id}/qa/members/{memberId}/reset-answers", quizSet.id, target)
                .with(authentication(admin())).with(csrf()),
        )
            .andExpect(redirectedUrl("/admin/quiz-sets/${quizSet.id}/participants"))
            .andExpect(flash().attribute("message", containsString("dummy-male-0001 (#$target)의 답·진행을 초기화했습니다.")))

        quizProgressRepository.findByMemberIdAndQuizSetId(target, quizSet.id) shouldBe null
        quizProgressRepository.findByMemberIdAndQuizSetId(other, quizSet.id) shouldNotBe null
    }

    @Test
    @DisplayName("검색한 채 회원별 초기화를 하면 같은 검색어로 참여 현황에 돌아간다")
    fun resetMemberQuizAnswersKeepsSearchQuery() {
        val quizSet = quizSetRepository.save(QuizSetFixture.currentWeek())
        val target = saveCompletedMember(quizSet.id, "dummy-male-0001")

        mockMvc.perform(
            post("/admin/quiz-sets/{id}/qa/members/{memberId}/reset-answers", quizSet.id, target)
                .param("q", " 홍 길동 ")
                .with(authentication(admin())).with(csrf()),
        )
            .andExpect(redirectedUrl("/admin/quiz-sets/${quizSet.id}/participants?q=%ED%99%8D%20%EA%B8%B8%EB%8F%99"))
    }

    @Test
    @DisplayName("초기화한 회원만 #ID로 찾던 중이면 검색어 없이 참여 현황에 돌아간다")
    fun resetMemberQuizAnswersDropsSearchForResetMember() {
        val quizSet = quizSetRepository.save(QuizSetFixture.currentWeek())
        val target = saveCompletedMember(quizSet.id, "dummy-male-0001")

        mockMvc.perform(
            post("/admin/quiz-sets/{id}/qa/members/{memberId}/reset-answers", quizSet.id, target)
                .param("q", "#$target")
                .with(authentication(admin())).with(csrf()),
        )
            .andExpect(redirectedUrl("/admin/quiz-sets/${quizSet.id}/participants"))
    }

    @Test
    @DisplayName("검색어가 공백뿐이면 회원별 초기화 뒤 검색어 없이 참여 현황에 돌아간다")
    fun resetMemberQuizAnswersIgnoresBlankSearch() {
        val quizSet = quizSetRepository.save(QuizSetFixture.currentWeek())
        val target = saveCompletedMember(quizSet.id, "dummy-male-0001")

        mockMvc.perform(
            post("/admin/quiz-sets/{id}/qa/members/{memberId}/reset-answers", quizSet.id, target)
                .param("q", "   ")
                .with(authentication(admin())).with(csrf()),
        )
            .andExpect(redirectedUrl("/admin/quiz-sets/${quizSet.id}/participants"))
    }

    @Test
    @DisplayName("회원별 초기화가 거부되면 참여 현황으로 돌아가 이유를 보여 준다")
    fun resetMemberQuizAnswersRejected() {
        val quizSet = quizSetRepository.save(QuizSetFixture.currentWeek())

        mockMvc.perform(
            post("/admin/quiz-sets/{id}/qa/members/{memberId}/reset-answers", quizSet.id, 99999L)
                .param("q", "#99999")
                .with(authentication(admin())).with(csrf()),
        )
            .andExpect(redirectedUrl("/admin/quiz-sets/${quizSet.id}/participants?q=%2399999"))
            .andExpect(flash().attribute("error", containsString("이 퀴즈셋에 참여하지 않은 회원입니다.")))
    }

    @Test
    @DisplayName("강제 삭제는 매칭이 끝난 퀴즈셋도 지우고 목록으로 돌아간다")
    fun forceDeleteQuizSet() {
        val quizSet = quizSetRepository.save(QuizSetFixture.create())
        personalMatchRepository.save(PersonalMatchFixture.create(1L, 2L, quizSet.id))

        mockMvc.perform(post("/admin/quiz-sets/{id}/qa/force-delete", quizSet.id).with(authentication(admin())).with(csrf()))
            .andExpect(redirectedUrl("/admin/quiz-sets"))
            .andExpect(flash().attribute("message", containsString("퀴즈셋 #${quizSet.id}(${quizSet.title})을 강제 삭제했습니다.")))

        quizSetRepository.existsById(quizSet.id) shouldBe false
    }

    @Test
    @DisplayName("없는 퀴즈셋에 QA 도구를 쓰면 목록으로 돌아가 오류를 보여 준다")
    fun qaToolsOnMissingQuizSet() {
        mockMvc.perform(post("/admin/quiz-sets/{id}/qa/reset-matching", 99999L).with(authentication(admin())).with(csrf()))
            .andExpect(redirectedUrl("/admin/quiz-sets"))
            .andExpect(flash().attributeExists("error"))
    }

    @Test
    @DisplayName("퀴즈셋 활성/비활성/삭제")
    fun quizSetMutations() {
        val quizSet = quizSetRepository.save(QuizSetFixture.create(isActive = false))
        val id = quizSet.id

        mockMvc.perform(post("/admin/quiz-sets/{id}/activate", id).with(authentication(admin())).with(csrf()))
            .andExpect(status().is3xxRedirection)
        mockMvc.perform(post("/admin/quiz-sets/{id}/deactivate", id).with(authentication(admin())).with(csrf()))
            .andExpect(status().is3xxRedirection)
        mockMvc.perform(post("/admin/quiz-sets/{id}/delete", id).with(authentication(admin())).with(csrf()))
            .andExpect(status().is3xxRedirection)
    }

    @Test
    @DisplayName("매칭이 진행된 퀴즈셋은 삭제 버튼 대신 안내를 보이고, 삭제 요청은 상세로 돌려보내 사유를 알린다")
    fun matchedQuizSetDeletionRejected() {
        val quizSet = quizSetRepository.save(QuizSetFixture.create(isActive = false))
        groupMatchRepository.save(GroupMatchFixture.create(quizSetId = quizSet.id))

        mockMvc.perform(get("/admin/quiz-sets/{id}", quizSet.id).with(authentication(admin())))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("매칭이 진행된 퀴즈셋이라 삭제할 수 없습니다")))
            .andExpect(content().string(not(containsString("/admin/quiz-sets/${quizSet.id}/delete"))))
        mockMvc.perform(post("/admin/quiz-sets/{id}/delete", quizSet.id).with(authentication(admin())).with(csrf()))
            .andExpect(redirectedUrl("/admin/quiz-sets/${quizSet.id}"))
            .andExpect(flash().attribute("error", containsString("매칭이 진행된 퀴즈셋은 삭제할 수 없습니다")))

        quizSetRepository.existsById(quizSet.id) shouldBe true
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
    @DisplayName("더미 생성 페이지 렌더 + 생성/삭제")
    fun dummyPage() {
        mockMvc.perform(get("/admin/dummy").with(authentication(admin()))).andExpect(status().isOk)

        val quizSet = quizSetRepository.save(QuizSetFixture.create())
        val quiz = quizRepository.save(QuizFixture.create(quizSetId = quizSet.id, displayOrder = 1))
        quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id, content = "A", displayOrder = 1))
        quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id, content = "B", displayOrder = 2))

        mockMvc.perform(
            post("/admin/dummy").with(authentication(admin())).with(csrf())
                .param("quizSetId", quizSet.id.toString())
                .param("maleCount", "2").param("femaleCount", "2")
                .param("minAge", "20").param("maxAge", "30"),
        ).andExpect(status().is3xxRedirection)
            .andExpect(flash().attribute("createdQuizSetId", quizSet.id))
        mockMvc.perform(get("/admin/dummy").with(authentication(admin())).flashAttr("createdQuizSetId", quizSet.id))
            .andExpect(content().string(containsString("/admin/quiz-sets/${quizSet.id}/participants")))

        mockMvc.perform(post("/admin/dummy/clear").with(authentication(admin())).with(csrf()))
            .andExpect(status().is3xxRedirection)
    }

    @Test
    @DisplayName("회원 관리 페이지 — 검색 전/검색 결과 렌더")
    fun memberSearchPage() {
        // 검색 전 빈 상태
        mockMvc.perform(get("/admin/members").with(authentication(admin()))).andExpect(status().isOk)

        // 같은 이메일을 가진 회원 2명
        memberRepository.save(MemberFixture.create(nickname = "m1", email = "dup@ditto.pics", role = MemberRole.USER))
        memberRepository.save(MemberFixture.create(nickname = "m2", email = "dup@ditto.pics", role = MemberRole.ADMIN))

        mockMvc.perform(get("/admin/members").param("email", "dup@ditto.pics").with(authentication(admin())))
            .andExpect(status().isOk)
    }

    @Test
    @DisplayName("회원 권한 변경 후 검색어 유지 리다이렉트")
    fun memberRoleChange() {
        val member = memberRepository.save(
            MemberFixture.create(nickname = "rolechg", email = "role@ditto.pics", role = MemberRole.USER),
        )

        mockMvc.perform(
            post("/admin/members/{id}/role", member.id).with(authentication(admin())).with(csrf())
                .param("role", "ADMIN").param("email", "role@ditto.pics"),
        ).andExpect(status().is3xxRedirection)
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
    @DisplayName("로컬 개발 로그인은 세션 설정 후 대시보드로 이동한다")
    fun devLoginRedirect() {
        mockMvc.perform(get("/admin/oauth/dev"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/admin"))
    }

    @Test
    @DisplayName("로컬 개발 로그인 세션으로 어드민 페이지에 접근할 수 있다")
    fun devLoginSessionGrantsAccess() {
        val session = mockMvc.perform(get("/admin/oauth/dev"))
            .andReturn().request.session as MockHttpSession

        mockMvc.perform(get("/admin").session(session)).andExpect(status().isOk)
    }

    @Test
    @DisplayName("local 프로파일에서 로그인 페이지에 로컬 개발 로그인 버튼이 노출된다")
    fun loginPageShowsDevLoginButton() {
        mockMvc.perform(get("/admin/login"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("로컬 개발 로그인")))
    }

    @Test
    @DisplayName("로그인 페이지 에러/로그아웃 메시지 분기")
    fun loginPageMessages() {
        mockMvc.perform(get("/admin/login").param("error", "")).andExpect(status().isOk)
        mockMvc.perform(get("/admin/login").param("logout", "")).andExpect(status().isOk)
    }

    @Test
    @DisplayName("퀴즈셋 수정 폼은 저장된 주차(월요일)를 ISO 날짜로 렌더링한다")
    fun editFormRendersStoredWeek() {
        val quizSet = quizSetRepository.save(QuizSetFixture.create())

        mockMvc.perform(get("/admin/quiz-sets/{id}/edit", quizSet.id).with(authentication(admin())))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("name=\"weekStartedOn\" value=\"2026-04-06\"")))
    }

    @Test
    @DisplayName("수정 폼이 문항·선택지를 함께 저장하고 좌우 순서가 뒤바뀐다")
    fun quizUpdateAndRegenerate() {
        val quizSet = quizSetRepository.save(QuizSetFixture.create())
        val id = quizSet.id
        val quiz = quizRepository.save(QuizFixture.create(quizSetId = id, displayOrder = 1))
        val first = quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id, content = "A", displayOrder = 1))
        val second = quizChoiceRepository.save(QuizChoiceFixture.create(quizId = quiz.id, content = "B", displayOrder = 2))

        mockMvc.perform(get("/admin/quiz-sets/{id}/edit", id).with(authentication(admin())))
            .andExpect(status().isOk)
        mockMvc.perform(
            post("/admin/quiz-sets/{id}", id).with(authentication(admin())).with(csrf())
                .param("category", "수정").param("title", "수정 제목").param("description", "d")
                .param("weekStartedOn", "2026-06-15")
                .param("matchingType", "ONE_TO_ONE").param("isActive", "false")
                .param("quizzes[0].id", quiz.id.toString())
                .param("quizzes[0].question", "수정된 질문")
                .param("quizzes[0].choices[0].id", second.id.toString())
                .param("quizzes[0].choices[0].content", "B")
                .param("quizzes[0].choices[1].id", first.id.toString())
                .param("quizzes[0].choices[1].content", "A로 수정"),
        ).andExpect(status().is3xxRedirection)

        quizRepository.findByQuizSetIdOrderByDisplayOrderAsc(id).first().question shouldBe "수정된 질문"
        quizChoiceRepository.findByQuizIdOrderByDisplayOrderAsc(quiz.id)
            .map { it.id to it.content } shouldBe listOf(second.id to "B", first.id to "A로 수정")

        mockMvc.perform(post("/admin/matching/quiz-sets/{id}/regenerate", id).with(authentication(admin())).with(csrf()))
            .andExpect(status().is3xxRedirection)
            .andExpect(flash().attributeExists("message", "regeneration"))
            .andExpect(flash().attributeCount(2))
    }

    @Test
    @DisplayName("매칭 화면은 재생성 결과 flash 가 있으면 후보 풀·행 수·매칭 표를 그린다")
    fun matchingPageRendersRegenerationSummary() {
        val summary = CandidateGenerationSummary(
            quizSetId = 7L,
            matchingType = MatchingType.ONE_TO_ONE,
            participantCount = 3,
            rowCounts = CandidateRowCounts(deletedCount = 4, savedCount = 2),
            matches = listOf(ScoredMatch.duo(12L, 5L, MatchScore(score = 100.0, matchedQuestionCount = 2, totalQuestionCount = 2))),
        )

        mockMvc.perform(get("/admin/matching").with(authentication(admin())).flashAttr("regeneration", summary))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("재생성 결과")))
            .andExpect(content().string(containsString("5, 12")))
            .andExpect(content().string(containsString("2 / 2")))
            .andExpect(content().string(containsString("/admin/quiz-sets/7/participants")))
    }

    @Test
    @DisplayName("응답이 시작된 그룹 퀴즈셋의 매칭 재생성은 성공 메시지 대신 실패 메시지를 남긴다")
    fun regenerateRejectedWhenGroupAlreadyResponded() {
        val quizSet = quizSetRepository.save(QuizSetFixture.create(matchingType = MatchingType.GROUP))
        groupMatchRepository.save(GroupMatchFixture.create(quizSetId = quizSet.id, acceptedCount = 3))

        mockMvc.perform(post("/admin/matching/quiz-sets/{id}/regenerate", quizSet.id).with(authentication(admin())).with(csrf()))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/admin/matching"))
            .andExpect(flash().attribute("error", containsString("이미 그룹 매칭에 응답한 회원이 있어")))
            .andExpect(flash().attribute("message", null))
    }

    @Test
    @DisplayName("신고 목록·상세 페이지가 렌더링된다")
    fun reportPages() {
        val reporter = memberRepository.save(MemberFixture.create(nickname = "신고자", status = MemberStatus.ACTIVE))
        val reported = memberRepository.save(MemberFixture.create(nickname = "피신고자", status = MemberStatus.ACTIVE))
        val report = memberReportRepository.save(
            MemberReportFixture.create(reporterId = reporter.id, reportedMemberId = reported.id),
        )

        mockMvc.perform(get("/admin/reports").with(authentication(admin()))).andExpect(status().isOk)
        mockMvc.perform(get("/admin/reports/{id}", report.id).with(authentication(admin()))).andExpect(status().isOk)
    }

    @Test
    @DisplayName("신고 검토 처리 후 상세로 리다이렉트된다")
    fun reviewReport() {
        val reporter = memberRepository.save(MemberFixture.create(nickname = "신고자2", status = MemberStatus.ACTIVE))
        val reported = memberRepository.save(MemberFixture.create(nickname = "피신고자2", status = MemberStatus.ACTIVE))
        val report = memberReportRepository.save(
            MemberReportFixture.create(reporterId = reporter.id, reportedMemberId = reported.id),
        )

        mockMvc.perform(
            post("/admin/reports/{id}/action", report.id)
                .with(authentication(admin())).with(csrf())
                .param("decision", "REJECT").param("reviewNote", "근거 부족"),
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/admin/reports/" + report.id))
    }

    @Test
    @DisplayName("회원 제재 관리 페이지 렌더·직권 제재·해제")
    fun memberSanctions() {
        val member = memberRepository.save(MemberFixture.create(nickname = "제재대상", status = MemberStatus.ACTIVE))

        mockMvc.perform(get("/admin/members/{id}/sanctions", member.id).with(authentication(admin())))
            .andExpect(status().isOk)

        mockMvc.perform(
            post("/admin/members/{id}/sanctions", member.id)
                .with(authentication(admin())).with(csrf())
                .param("level", "SUSPENSION").param("origin", "MANUAL").param("note", "직권"),
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/admin/members/" + member.id + "/sanctions"))
    }

    @Test
    @DisplayName("애플 로그인 진입은 인가 URL로 리다이렉트된다")
    fun appleOauthAuthorizeRedirect() {
        mockMvc.perform(get("/admin/oauth/apple")).andExpect(status().is3xxRedirection)
    }

    @Test
    @DisplayName("ADMIN 회원 애플 콜백은 세션 설정 후 대시보드로 이동한다")
    fun appleCallbackAdmin() {
        val member = memberRepository.save(MemberFixture.create(role = MemberRole.ADMIN).apply { activate() })
        socialAccountRepository.save(
            SocialAccount.create(
                memberId = member.id,
                provider = SocialProvider.APPLE,
                providerUserId = AppleNativeFakeAuthenticator.FAKE_SUBJECT,
            ),
        )

        val result = mockMvc.perform(
            post("/admin/oauth/apple/callback")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("id_token", "fake-id-token"),
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/admin"))
            .andReturn()

        // 콜백이 심은 세션만으로 어드민 페이지에 들어갈 수 있어야 한다.
        val session = result.request.session as MockHttpSession
        mockMvc.perform(get("/admin").session(session)).andExpect(status().isOk)
    }

    @Test
    @DisplayName("애플 콜백은 CSRF 토큰 없이도 처리된다 — 애플이 보내는 크로스사이트 폼 POST다")
    fun appleCallbackSkipsCsrf() {
        // .with(csrf()) 없이 호출한다. CSRF 예외가 풀리면 403 이 되어 이 테스트가 깨진다.
        mockMvc.perform(
            post("/admin/oauth/apple/callback")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("id_token", "fake-id-token"),
        ).andExpect(status().is3xxRedirection)
    }

    @Test
    @DisplayName("애플로 연결된 회원이 없으면 로그인 에러로 이동한다")
    fun appleCallbackDenied() {
        mockMvc.perform(
            post("/admin/oauth/apple/callback")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("id_token", "fake-id-token"),
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/admin/login?error"))
    }

    @Test
    @DisplayName("ADMIN이 아닌 애플 회원은 로그인 에러로 이동한다")
    fun appleCallbackNonAdmin() {
        val member = memberRepository.save(MemberFixture.create(role = MemberRole.USER).apply { activate() })
        socialAccountRepository.save(
            SocialAccount.create(
                memberId = member.id,
                provider = SocialProvider.APPLE,
                providerUserId = AppleNativeFakeAuthenticator.FAKE_SUBJECT,
            ),
        )

        mockMvc.perform(
            post("/admin/oauth/apple/callback")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("id_token", "fake-id-token"),
        )
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/admin/login?error"))
    }

    @Test
    @DisplayName("로그인 페이지에 애플 로그인 버튼이 노출된다")
    fun loginPageShowsAppleButton() {
        mockMvc.perform(get("/admin/login"))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Apple로 로그인")))
    }
}
