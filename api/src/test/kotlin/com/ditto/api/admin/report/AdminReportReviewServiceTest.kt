package com.ditto.api.admin.report

import com.ditto.api.admin.auth.AdminPrincipal
import com.ditto.api.admin.report.dto.ReviewDecision
import com.ditto.api.auth.service.AuthService
import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.memberreport.MemberReportFixture
import com.ditto.domain.memberreport.entity.MemberReportStatus
import com.ditto.domain.memberreport.repository.MemberReportRepository
import com.ditto.domain.refreshtoken.repository.RefreshTokenRepository
import com.ditto.domain.sanction.entity.SanctionLevel
import com.ditto.domain.sanction.entity.SanctionOrigin
import com.ditto.domain.sanction.entity.SanctionStatus
import com.ditto.domain.sanction.repository.SanctionRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.DayOfWeek
import java.time.LocalDateTime
import javax.sql.DataSource

class AdminReportReviewServiceTest(
    private val adminReportReviewService: AdminReportReviewService,
    private val memberRepository: MemberRepository,
    private val memberReportRepository: MemberReportRepository,
    private val sanctionRepository: SanctionRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val authService: AuthService,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    // 2026-07-15 수요일 — 차주 월요일은 2026-07-20
    val now = LocalDateTime.of(2026, 7, 15, 10, 0)
    val admin = AdminPrincipal(memberId = 999L, name = "관리자", email = "admin@ditto.pics")

    fun saveActiveMember(nickname: String): Member =
        memberRepository.save(MemberFixture.create(nickname = nickname, status = MemberStatus.ACTIVE))

    fun saveReport(reporterId: Long, reportedMemberId: Long) =
        memberReportRepository.save(
            MemberReportFixture.create(reporterId = reporterId, reportedMemberId = reportedMemberId),
        )

    "검토 처리" - {
        "2주 정지: 신고 종결 + 제재 생성 + 회원 정지 + refresh 전량 회수가 한 번에 일어난다" {
            val reporter = saveActiveMember("신고자")
            val reported = saveActiveMember("피신고자")
            val refreshToken = authService.createRefreshToken(reported.id)
            val report = saveReport(reporter.id, reported.id)

            adminReportReviewService.review(report.id, ReviewDecision.SUSPENSION, "메모", admin, now)

            memberReportRepository.findById(report.id).orElseThrow().status shouldBe MemberReportStatus.ACTIONED
            val sanction = sanctionRepository.findAllByMemberIdOrderByIdDesc(reported.id).single()
            sanction.level shouldBe SanctionLevel.SUSPENSION
            sanction.origin shouldBe SanctionOrigin.REPORTED
            sanction.memberReportId shouldBe report.id
            sanction.endsAt shouldBe now.plusDays(14)
            val reloaded = memberRepository.findById(reported.id).orElseThrow()
            reloaded.status shouldBe MemberStatus.SUSPENDED
            reloaded.suspendedUntil shouldBe now.plusDays(14)
            refreshTokenRepository.findByToken(refreshToken.token).shouldBeNull()
        }

        "경고: 차주 월요일부터 7일 구간의 제재만 생성하고 계정 상태는 그대로다" {
            val reporter = saveActiveMember("신고자")
            val reported = saveActiveMember("피신고자")
            val refreshToken = authService.createRefreshToken(reported.id)
            val report = saveReport(reporter.id, reported.id)

            adminReportReviewService.review(report.id, ReviewDecision.WARNING, null, admin, now)

            val sanction = sanctionRepository.findAllByMemberIdOrderByIdDesc(reported.id).single()
            sanction.startsAt shouldBe LocalDateTime.of(2026, 7, 20, 0, 0)
            sanction.startsAt.dayOfWeek shouldBe DayOfWeek.MONDAY
            sanction.endsAt shouldBe LocalDateTime.of(2026, 7, 27, 0, 0)
            memberRepository.findById(reported.id).orElseThrow().status shouldBe MemberStatus.ACTIVE
            // 경고는 계정 세션을 회수하지 않는다.
            refreshTokenRepository.findByToken(refreshToken.token) shouldNotBe null
        }

        "영구 차단: 종료 없는 제재 + BANNED 전이" {
            val reporter = saveActiveMember("신고자")
            val reported = saveActiveMember("피신고자")
            val report = saveReport(reporter.id, reported.id)

            adminReportReviewService.review(report.id, ReviewDecision.PERMANENT_BAN, null, admin, now)

            val sanction = sanctionRepository.findAllByMemberIdOrderByIdDesc(reported.id).single()
            sanction.level shouldBe SanctionLevel.PERMANENT_BAN
            sanction.endsAt.shouldBeNull()
            memberRepository.findById(reported.id).orElseThrow().status shouldBe MemberStatus.BANNED
        }

        "기각: 신고만 종결하고 제재·계정 변화가 없다" {
            val reporter = saveActiveMember("신고자")
            val reported = saveActiveMember("피신고자")
            val report = saveReport(reporter.id, reported.id)

            adminReportReviewService.review(report.id, ReviewDecision.REJECT, "근거 부족", admin, now)

            memberReportRepository.findById(report.id).orElseThrow().status shouldBe MemberReportStatus.REJECTED
            sanctionRepository.findAllByMemberIdOrderByIdDesc(reported.id) shouldBe emptyList()
            memberRepository.findById(reported.id).orElseThrow().status shouldBe MemberStatus.ACTIVE
        }

        "이미 종결된 신고는 다시 처리할 수 없다" {
            val reporter = saveActiveMember("신고자")
            val reported = saveActiveMember("피신고자")
            val report = saveReport(reporter.id, reported.id)
            adminReportReviewService.review(report.id, ReviewDecision.REJECT, null, admin, now)

            val exception = shouldThrow<WarnException> {
                adminReportReviewService.review(report.id, ReviewDecision.SUSPENSION, null, admin, now)
            }

            exception.errorCode shouldBe ErrorCode.REPORT_ALREADY_REVIEWED
            sanctionRepository.findAllByMemberIdOrderByIdDesc(reported.id) shouldBe emptyList()
        }

        "피신고자가 탈퇴했으면 제재 결정이 실패하고 신고 종결도 롤백된다" {
            val reporter = saveActiveMember("신고자")
            val report = saveReport(reporter.id, 99999L)

            val exception = shouldThrow<WarnException> {
                adminReportReviewService.review(report.id, ReviewDecision.SUSPENSION, null, admin, now)
            }

            exception.errorCode shouldBe ErrorCode.NOT_FOUND
            memberReportRepository.findById(report.id).orElseThrow().status shouldBe MemberReportStatus.RECEIVED
        }
    }
})
