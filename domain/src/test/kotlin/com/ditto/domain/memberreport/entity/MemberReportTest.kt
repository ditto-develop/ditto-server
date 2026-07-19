package com.ditto.domain.memberreport.entity

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.memberreport.MemberReportFixture
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class MemberReportTest : FreeSpec(
    {
        "receive" - {
            "정상 접수하면 RECEIVED 상태로 생성된다" {
                val report = MemberReportFixture.create(
                    reporterId = 1L,
                    reportedMemberId = 2L,
                    reason = MemberReportReason.MONEY_DEMAND,
                    source = MemberReportSource.MATCH_RESULT,
                )

                report.reporterId shouldBe 1L
                report.reportedMemberId shouldBe 2L
                report.reason shouldBe MemberReportReason.MONEY_DEMAND
                report.source shouldBe MemberReportSource.MATCH_RESULT
                report.status shouldBe MemberReportStatus.RECEIVED
            }

            "자기 자신을 신고하면 거부한다" {
                val exception = shouldThrow<WarnException> {
                    MemberReportFixture.create(reporterId = 1L, reportedMemberId = 1L)
                }

                exception.errorCode shouldBe ErrorCode.CANNOT_REPORT_SELF
            }

            "기타 사유는 상세 설명이 없으면 거부한다" {
                val exception = shouldThrow<WarnException> {
                    MemberReportFixture.create(reason = MemberReportReason.ETC, detail = null)
                }

                exception.errorCode shouldBe ErrorCode.REPORT_ETC_REASON_REQUIRED
            }

            "기타 사유도 상세 설명이 있으면 접수된다" {
                val report = MemberReportFixture.create(reason = MemberReportReason.ETC, detail = "직접 입력한 사유")

                report.detail shouldBe "직접 입력한 사유"
            }
        }

        "MemberReportReason.from" - {
            "유효한 code면 해당 사유를 반환한다" {
                MemberReportReason.from("inappropriate-behavior") shouldBe MemberReportReason.INAPPROPRIATE_BEHAVIOR
            }

            "유효하지 않은 code면 BAD_REQUEST 예외가 발생한다" {
                val exception = shouldThrow<WarnException> {
                    MemberReportReason.from("unknown-code")
                }
                exception.errorCode shouldBe ErrorCode.BAD_REQUEST
            }
        }

        "MemberReportSource.from" - {
            "유효한 code면 해당 접수 위치를 반환한다" {
                MemberReportSource.from("profile") shouldBe MemberReportSource.PROFILE
            }

            "유효하지 않은 code면 BAD_REQUEST 예외가 발생한다" {
                val exception = shouldThrow<WarnException> {
                    MemberReportSource.from("unknown-code")
                }
                exception.errorCode shouldBe ErrorCode.BAD_REQUEST
            }
        }
    },
)
