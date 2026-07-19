package com.ditto.domain.memberreport.repository

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.memberreport.MemberReportFixture
import com.ditto.domain.memberreport.entity.MemberReportStatus
import com.ditto.domain.support.IntegrationTest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

class MemberReportRepositoryTest(
    private val memberReportRepository: MemberReportRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val reviewedAt = LocalDateTime.of(2026, 7, 20, 10, 0)

    fun completeReview(id: Long, result: MemberReportStatus = MemberReportStatus.ACTIONED): Int =
        memberReportRepository.completeReview(
            id = id,
            result = result,
            reviewedBy = 99L,
            reviewerName = "관리자",
            reviewedAt = reviewedAt,
            reviewNote = "검토 완료",
        )

    "completeReview" - {
        "RECEIVED 신고를 종결하고 검토 정보를 기록한다" {
            val report = memberReportRepository.save(MemberReportFixture.create())

            val updated = completeReview(report.id)

            updated shouldBe 1
            val reloaded = memberReportRepository.findById(report.id).orElseThrow()
            reloaded.status shouldBe MemberReportStatus.ACTIONED
            reloaded.reviewedBy shouldBe 99L
            reloaded.reviewerName shouldBe "관리자"
            reloaded.reviewedAt shouldBe reviewedAt
            reloaded.reviewNote shouldBe "검토 완료"
        }

        "이미 종결된 신고는 갱신하지 않는다 — 이중 검토 방어" {
            val report = memberReportRepository.save(MemberReportFixture.create())
            completeReview(report.id, result = MemberReportStatus.REJECTED)

            val secondAttempt = completeReview(report.id, result = MemberReportStatus.ACTIONED)

            secondAttempt shouldBe 0
            memberReportRepository.findById(report.id).orElseThrow().status shouldBe MemberReportStatus.REJECTED
        }

        "RECEIVED로의 전이는 거부한다" {
            val report = memberReportRepository.save(MemberReportFixture.create())

            val exception = shouldThrow<WarnException> {
                completeReview(report.id, result = MemberReportStatus.RECEIVED)
            }

            exception.errorCode shouldBe ErrorCode.INVALID_STATUS_TRANSITION
        }
    }
})
