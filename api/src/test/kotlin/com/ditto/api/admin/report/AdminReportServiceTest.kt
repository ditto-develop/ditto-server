package com.ditto.api.admin.report

import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.memberreport.MemberReportFixture
import com.ditto.domain.memberreport.entity.MemberReportReason
import com.ditto.domain.memberreport.entity.MemberReportStatus
import com.ditto.domain.memberreport.repository.MemberReportRepository
import com.ditto.domain.sanction.SanctionFixture
import com.ditto.domain.sanction.entity.SanctionLevel
import com.ditto.domain.sanction.entity.SanctionOrigin
import com.ditto.domain.sanction.repository.SanctionRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.LocalDateTime
import javax.sql.DataSource

class AdminReportServiceTest(
    private val adminReportService: AdminReportService,
    private val memberRepository: MemberRepository,
    private val memberReportRepository: MemberReportRepository,
    private val sanctionRepository: SanctionRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun saveActiveMember(nickname: String): Member =
        memberRepository.save(MemberFixture.create(nickname = nickname, status = MemberStatus.ACTIVE))

    "신고 목록" - {
        "접수 오래된 순으로 정렬하고 24시간 초과 건을 표시한다" {
            val reporter = saveActiveMember("신고자")
            val reported = saveActiveMember("피신고자")
            val report = memberReportRepository.save(
                MemberReportFixture.create(reporterId = reporter.id, reportedMemberId = reported.id),
            )

            val overdueNow = report.createdAt.plusHours(25)
            val rows = adminReportService.listReports(MemberReportStatus.RECEIVED, overdueNow)

            rows.size shouldBe 1
            rows[0].overdue shouldBe true
            rows[0].elapsedText shouldContain "1일"
        }

        "24시간 이내 건은 초과 표시가 없다" {
            val reporter = saveActiveMember("신고자")
            val reported = saveActiveMember("피신고자")
            val report = memberReportRepository.save(
                MemberReportFixture.create(reporterId = reporter.id, reportedMemberId = reported.id),
            )

            val rows = adminReportService.listReports(MemberReportStatus.RECEIVED, report.createdAt.plusHours(3))

            rows[0].overdue shouldBe false
        }
    }

    "신고 상세" - {
        "피신고자의 추천 차수는 유효 제재 수 + 1 — 허위 신고자 제재와 직권 해제는 제외한다" {
            val reporter = saveActiveMember("신고자")
            val reported = saveActiveMember("피신고자")
            val report = memberReportRepository.save(
                MemberReportFixture.create(reporterId = reporter.id, reportedMemberId = reported.id),
            )
            val startsAt = LocalDateTime.of(2026, 6, 1, 0, 0)
            // 산입: 신고 기반 경고 1건
            sanctionRepository.save(
                SanctionFixture.create(
                    memberId = reported.id,
                    origin = SanctionOrigin.REPORTED,
                    level = SanctionLevel.WARNING,
                    startsAt = startsAt,
                    endsAt = startsAt.plusDays(7),
                ),
            )
            // 제외: 허위 신고자 제재
            sanctionRepository.save(
                SanctionFixture.create(memberId = reported.id, origin = SanctionOrigin.FALSE_REPORT, startsAt = startsAt),
            )
            // 제외: 직권 해제(오처리 정정)
            sanctionRepository.save(
                SanctionFixture.create(memberId = reported.id, origin = SanctionOrigin.MANUAL, startsAt = startsAt)
                    .apply { lift() },
            )

            val detail = adminReportService.getReportDetail(report.id, report.createdAt.plusHours(1))

            detail.reported.recommendedStrike shouldBe 2
            detail.reported.sanctions.size shouldBe 3
        }

        "신고자의 총 신고 수와 악의 기각 수를 함께 보여준다" {
            val reporter = saveActiveMember("신고자")
            val reported = saveActiveMember("피신고자")
            val first = memberReportRepository.save(
                MemberReportFixture.create(reporterId = reporter.id, reportedMemberId = reported.id),
            )
            memberReportRepository.completeReview(
                id = first.id,
                result = MemberReportStatus.REJECTED_ABUSIVE,
                reviewedBy = 99L,
                reviewerName = "관리자",
                reviewedAt = LocalDateTime.of(2026, 7, 1, 10, 0),
                reviewNote = null,
            )
            val second = memberReportRepository.save(
                MemberReportFixture.create(
                    reporterId = reporter.id,
                    reportedMemberId = reported.id,
                    reason = MemberReportReason.MONEY_DEMAND,
                ),
            )

            val detail = adminReportService.getReportDetail(second.id, second.createdAt)

            detail.reporter.totalReportCount shouldBe 2
            detail.reporter.abusiveRejectedCount shouldBe 1
            detail.isSevere shouldBe true
        }

        "탈퇴한 회원은 대체 표기로 보여준다" {
            val reporter = saveActiveMember("신고자")
            val report = memberReportRepository.save(
                MemberReportFixture.create(reporterId = reporter.id, reportedMemberId = 99999L),
            )

            val detail = adminReportService.getReportDetail(report.id, report.createdAt)

            detail.reported.nickname shouldContain "탈퇴한 회원"
            detail.reported.statusName shouldBe "탈퇴"
        }

        "종결된 신고는 검토 결과를 담는다" {
            val reporter = saveActiveMember("신고자")
            val reported = saveActiveMember("피신고자")
            val report = memberReportRepository.save(
                MemberReportFixture.create(reporterId = reporter.id, reportedMemberId = reported.id),
            )
            memberReportRepository.completeReview(
                id = report.id,
                result = MemberReportStatus.REJECTED,
                reviewedBy = 99L,
                reviewerName = "관리자",
                reviewedAt = LocalDateTime.of(2026, 7, 1, 10, 0),
                reviewNote = "근거 부족",
            )

            val detail = adminReportService.getReportDetail(report.id, report.createdAt.plusHours(1))

            val review = detail.review.shouldNotBeNull()
            review.reviewerName shouldBe "관리자"
            review.note shouldBe "근거 부족"
            detail.received shouldBe false
        }

        "존재하지 않는 신고는 NOT_FOUND" {
            val exception = shouldThrow<WarnException> {
                adminReportService.getReportDetail(99999L, LocalDateTime.of(2026, 7, 1, 0, 0))
            }
            exception.errorCode shouldBe ErrorCode.NOT_FOUND
        }
    }
})
