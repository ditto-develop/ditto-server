package com.ditto.api.admin.qa

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.api.admin.qa.dto.QaReportSection
import com.ditto.api.support.IntegrationTest
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

    fun reportAsDummy(dummy: Member, target: Member, configure: MockHttpServletRequestBuilder.() -> Unit = {}) =
        mockMvc.perform(
            post("/admin/qa/reports")
                .param("dummyId", dummy.id.toString())
                .param("reportedMemberId", target.id.toString())
                .param("source", MemberReportSource.CHAT_ROOM.code)
                .param("reasons", MemberReportReason.INAPPROPRIATE_BEHAVIOR.code)
                .apply(configure)
                .with(authentication(admin)).with(csrf()),
        )

    fun reportsBy(member: Member): List<MemberReport> =
        memberReportRepository.findByReporterIdInOrderByIdDesc(listOf(member.id), Limit.unlimited())

    "신고 제출" - {
        "더미가 앱 신고 API로 실회원을 신고하면 검토 대기로 접수된다" {
            val tester = saveMember("테스터")
            val dummy = saveMember("dummy-female-aaaa")

            reportAsDummy(dummy, tester) { param("reasons", MemberReportReason.MONEY_DEMAND.code) }
                .andExpect(redirectedUrl("/admin/qa#report"))
                .andExpect(flash().attribute("message", "${labelOf(dummy)} · ${labelOf(tester)} 신고 완료"))

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

            memberBlockRepository.existsByBlockerIdAndBlockedMemberId(dummy.id, tester.id) shouldBe true
        }

        "회원 ID를 직접 넣으면 목록에서 고른 회원보다 앞선다" {
            val listed = saveMember("목록회원")
            val typed = saveMember("직접입력회원")
            val dummy = saveMember("dummy-female-aaaa")

            reportAsDummy(dummy, listed) { param("typedMemberId", typed.id.toString()) }

            reportsBy(dummy).single().reportedMemberId shouldBe typed.id
        }

        "기타 사유에 상세가 없으면 앱의 거부를 코드와 함께 띄운다" {
            val tester = saveMember("테스터")
            val dummy = saveMember("dummy-female-aaaa")

            mockMvc.perform(
                post("/admin/qa/reports")
                    .param("dummyId", dummy.id.toString())
                    .param("reportedMemberId", tester.id.toString())
                    .param("source", MemberReportSource.PROFILE.code)
                    .param("reasons", MemberReportReason.ETC.code)
                    .with(authentication(admin)).with(csrf()),
            ).andExpect(flash().attribute("error", containsString("${labelOf(tester)} 신고 실패")))
                .andExpect(flash().attribute("error", containsString("(코드 ")))

            reportsBy(dummy).shouldBeEmpty()
        }

        "앱처럼 상세 설명 길이를 검증한다" {
            val tester = saveMember("테스터")
            val dummy = saveMember("dummy-female-aaaa")

            reportAsDummy(dummy, tester) { param("detail", "가".repeat(MemberReport.DETAIL_MAX_LENGTH + 1)) }
                .andExpect(flash().attribute("error", containsString("상세 설명은 최대 500자까지 가능합니다.")))

            reportsBy(dummy).shouldBeEmpty()
        }

        "같은 회원을 다시 신고하면 앱의 중복 신고 거부를 띄운다" {
            val tester = saveMember("테스터")
            val dummy = saveMember("dummy-female-aaaa")
            reportAsDummy(dummy, tester)

            reportAsDummy(dummy, tester).andExpect(flash().attribute("error", containsString("신고 실패")))

            reportsBy(dummy).size shouldBe 1
        }

        "더미가 아닌 회원으로는 신고할 수 없다" {
            val tester = saveMember("테스터")
            val realMember = saveMember("실회원")

            reportAsDummy(realMember, tester).andExpect(flash().attribute("error", containsString("더미 회원만")))

            reportsBy(realMember).shouldBeEmpty()
        }
    }

    "콘솔 카드" - {
        "더미가 있던 방의 실회원을 대상 앞쪽에 두고, 더미가 낸 신고를 보여 준다" {
            val tester = saveMember("테스터")
            val dummy = saveMember("dummy-female-aaaa")
            val room = chatRoomRepository.save(ChatRoomFixture.personal())
            chatRoomMemberRepository.saveAll(listOf(tester, dummy).map { ChatRoomMemberFixture.create(room.id, it.id) })
            reportAsDummy(dummy, tester)

            val section = mockMvc.perform(get("/admin/qa").with(authentication(admin)))
                .andExpect(status().isOk)
                .andReturn().modelAndView.shouldNotBeNull().model["report"] as QaReportSection

            section.targets.first().member.id shouldBe tester.id
            section.reports.single().let {
                it.reported.id shouldBe tester.id
                it.isWaiting shouldBe true
            }
        }
    }
})
