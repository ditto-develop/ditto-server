package com.ditto.api.admin.qa

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.api.admin.qa.dto.QaReportSection
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorCode
import com.ditto.domain.chat.ChatRoomFixture
import com.ditto.domain.chat.ChatRoomMemberFixture
import com.ditto.domain.chat.repository.ChatRoomMemberRepository
import com.ditto.domain.chat.repository.ChatRoomRepository
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberBlockRepository
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.memberreport.entity.MemberReport
import com.ditto.domain.memberreport.entity.MemberReportReason
import com.ditto.domain.memberreport.entity.MemberReportSource
import com.ditto.domain.memberreport.entity.MemberReportStatus
import com.ditto.domain.memberreport.repository.MemberReportRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.hamcrest.CoreMatchers.containsString
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.data.domain.Limit
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import javax.sql.DataSource

@AutoConfigureMockMvc
class AdminQaReportWebTest(
    private val mockMvc: MockMvc,
    private val memberRepository: MemberRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val chatRoomMemberRepository: ChatRoomMemberRepository,
    private val memberReportRepository: MemberReportRepository,
    private val memberBlockRepository: MemberBlockRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val admin = UsernamePasswordAuthenticationToken(
        AdminPrincipal(1L, "관리자", "admin@ditto.pics"),
        null,
        listOf(SimpleGrantedAuthority("ROLE_ADMIN")),
    )

    fun saveMember(nickname: String): Member =
        memberRepository.save(
            MemberFixture.create(nickname = nickname, email = "$nickname@ditto.pics", status = MemberStatus.ACTIVE),
        )

    fun labelOf(member: Member) = "${member.nickname}(#${member.id})"

    /** 브라우저처럼 비어 있는 칸도 빈 문자열로 보낸다. */
    fun reportAsDummy(
        dummy: Member?,
        target: Member?,
        typedMemberId: Long? = null,
        reasons: List<MemberReportReason> = listOf(MemberReportReason.INAPPROPRIATE_BEHAVIOR),
        configure: MockHttpServletRequestBuilder.() -> Unit = {},
    ) = mockMvc.perform(
        post("/admin/qa/reports")
            .param("dummyId", dummy?.id?.toString() ?: "")
            .param("listedMemberId", target?.id?.toString() ?: "")
            .param("typedMemberId", typedMemberId?.toString() ?: "")
            .param("source", MemberReportSource.CHAT_ROOM.code)
            .param("reasons", *reasons.map { it.code }.toTypedArray())
            .param("detail", "")
            .apply(configure)
            .with(authentication(admin)).with(csrf()),
    )

    fun reportsBy(member: Member): List<MemberReport> =
        memberReportRepository.findByReporterIdInOrderByIdDesc(listOf(member.id), Limit.unlimited())

    fun reportSection(): QaReportSection =
        mockMvc.perform(get("/admin/qa").with(authentication(admin)))
            .andExpect(status().isOk)
            .andReturn().modelAndView.shouldNotBeNull().model["report"] as QaReportSection

    "신고 제출" - {
        "더미가 앱 신고 API로 실회원을 신고하면 검토 대기로 접수되고 신고 번호를 알린다" {
            val tester = saveMember("테스터")
            val dummy = saveMember("dummy-female-aaaa")

            val result = reportAsDummy(
                dummy,
                tester,
                reasons = listOf(MemberReportReason.INAPPROPRIATE_BEHAVIOR, MemberReportReason.MONEY_DEMAND),
            ).andExpect(redirectedUrl("/admin/qa#report"))

            val reportId = reportsBy(dummy).single().id
            val expectedMessage = "${labelOf(dummy)} · ${labelOf(tester)} 신고 완료(신고 #$reportId)"
            result.andExpect(flash().attribute("message", expectedMessage))

            reportsBy(dummy).single().let {
                it.reportedMemberId shouldBe tester.id
                it.status shouldBe MemberReportStatus.RECEIVED
                it.source shouldBe MemberReportSource.CHAT_ROOM
                it.reasons shouldBe setOf(MemberReportReason.INAPPROPRIATE_BEHAVIOR, MemberReportReason.MONEY_DEMAND)
            }
        }

        "차단을 고르면 신고와 함께 차단도 만든다" {
            val tester = saveMember("테스터")
            val dummy = saveMember("dummy-female-aaaa")

            reportAsDummy(dummy, tester) { param("block", "true") }

            reportsBy(dummy).size shouldBe 1
            memberBlockRepository.existsByBlockerIdAndBlockedMemberId(dummy.id, tester.id) shouldBe true
        }

        "회원 ID를 직접 넣으면 목록에서 고른 회원 대신 그 회원을 신고한다" {
            val listed = saveMember("목록회원")
            val typed = saveMember("직접입력회원")
            val dummy = saveMember("dummy-female-aaaa")

            reportAsDummy(dummy, listed, typedMemberId = typed.id)

            reportsBy(dummy).single().reportedMemberId shouldBe typed.id
        }

        "더미나 신고할 회원을 고르지 않으면 신고하지 않고 알린다" {
            val tester = saveMember("테스터")

            reportAsDummy(dummy = null, target = tester)
                .andExpect(flash().attribute("error", "신고하는 더미와 신고할 회원을 골라야 신고할 수 있습니다."))

            memberReportRepository.count() shouldBe 0
        }

        "기타 사유에 상세가 없으면 앱의 거부를 코드와 함께 띄운다" {
            val tester = saveMember("테스터")
            val dummy = saveMember("dummy-female-aaaa")

            reportAsDummy(dummy, tester, reasons = listOf(MemberReportReason.ETC))
                .andExpect(flash().attribute("error", containsString("${labelOf(tester)} 신고 실패")))
                .andExpect(
                    flash().attribute("error", containsString("(코드 ${ErrorCode.REPORT_ETC_REASON_REQUIRED.code})")),
                )

            reportsBy(dummy).shouldBeEmpty()
        }

        "앱처럼 상세 설명 길이를 검증한다" {
            val tester = saveMember("테스터")
            val dummy = saveMember("dummy-female-aaaa")

            reportAsDummy(dummy, tester) { param("detail", "가".repeat(MemberReport.DETAIL_MAX_LENGTH + 1)) }
                .andExpect(flash().attribute("error", containsString("상세 설명은 최대 500자까지 가능합니다.")))

            reportsBy(dummy).shouldBeEmpty()
        }

        "검토 전에 같은 회원을 다시 신고하면 앱의 중복 신고 거부를 띄운다" {
            val tester = saveMember("테스터")
            val dummy = saveMember("dummy-female-aaaa")
            reportAsDummy(dummy, tester)

            reportAsDummy(dummy, tester)
                .andExpect(flash().attribute("error", containsString("(코드 ${ErrorCode.DUPLICATE_REPORT.code})")))

            reportsBy(dummy).size shouldBe 1
        }

        "영구 차단된 더미로는 앱처럼 신고할 수 없다" {
            val tester = saveMember("테스터")
            val bannedDummy = memberRepository.save(
                MemberFixture.create(
                    nickname = "dummy-female-aaaa",
                    email = "banned@ditto.pics",
                    status = MemberStatus.BANNED,
                ),
            )

            reportAsDummy(bannedDummy, tester)
                .andExpect(flash().attribute("error", containsString("(코드 ${ErrorCode.MEMBER_BANNED.code})")))

            reportsBy(bannedDummy).shouldBeEmpty()
        }

        "더미가 아닌 회원으로는 신고할 수 없다" {
            val tester = saveMember("테스터")
            val realMember = saveMember("실회원")

            reportAsDummy(realMember, tester).andExpect(flash().attribute("error", containsString("더미 회원만")))

            reportsBy(realMember).shouldBeEmpty()
        }
    }

    "콘솔 카드" - {
        "더미가 있던 방의 실회원을 맨 앞에 두고, 더미가 낸 신고를 보여 준다" {
            val tester = saveMember("테스터")
            val dummy = saveMember("dummy-female-aaaa")
            val room = chatRoomRepository.save(ChatRoomFixture.personal())
            chatRoomMemberRepository.saveAll(
                listOf(tester, dummy).map { ChatRoomMemberFixture.create(room.id, it.id) },
            )
            reportAsDummy(dummy, tester)

            val section = reportSection()

            section.targets.first().member.id shouldBe tester.id
            section.reports.single().let {
                it.reportedMember.id shouldBe tester.id
                it.isAwaitingReview shouldBe true
            }
        }

        "실회원이 없으면 기본 신고자를 대상 맨 뒤에 둬 자기 신고가 기본값이 되지 않는다" {
            val firstDummy = saveMember("dummy-female-aaaa")
            val secondDummy = saveMember("dummy-male-bbbb")

            val section = reportSection()

            section.dummies.first().id shouldBe firstDummy.id
            section.targets.map { it.member.id } shouldBe listOf(secondDummy.id, firstDummy.id)
        }
    }
})
