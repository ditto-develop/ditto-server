package com.ditto.api.userreport

import com.ditto.api.support.IntegrationTest
import com.ditto.api.userreport.dto.CreateUserReportRequest
import com.ditto.api.userreport.dto.ImageUploadFileRequest
import com.ditto.api.userreport.dto.IssueImageUploadUrlsRequest
import com.ditto.api.userreport.service.UserReportService
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.memberreport.MemberReportFixture
import com.ditto.domain.memberreport.entity.MemberReportStatus
import com.ditto.domain.memberreport.repository.MemberReportImageRepository
import com.ditto.domain.memberreport.repository.MemberReportRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import javax.sql.DataSource

class UserReportServiceTest(
    private val userReportService: UserReportService,
    private val memberRepository: MemberRepository,
    private val memberReportRepository: MemberReportRepository,
    private val memberReportImageRepository: MemberReportImageRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun saveActiveMember(nickname: String): Member =
        memberRepository.save(MemberFixture.create(nickname = nickname, status = MemberStatus.ACTIVE))

    fun issueUploadedKeys(memberId: Long, count: Int): List<String> {
        val request = IssueImageUploadUrlsRequest(
            files = List(count) { ImageUploadFileRequest(contentType = "image/png", contentLength = 1024L) },
        )
        return userReportService.issueImageUploadUrls(memberId, request).uploads.map { it.objectKey }
    }

    "신고 이미지 업로드 URL 발급" - {

        "요청한 파일 수만큼 업로드 URL과 객체 키를 발급한다" {
            val request = IssueImageUploadUrlsRequest(
                files = listOf(
                    ImageUploadFileRequest(contentType = "image/png", contentLength = 1024L),
                    ImageUploadFileRequest(contentType = "image/jpeg", contentLength = 2048L),
                    ImageUploadFileRequest(contentType = "image/webp", contentLength = 4096L),
                ),
            )

            val result = userReportService.issueImageUploadUrls(memberId = 1L, request = request)

            result.uploads.size shouldBe 3
            result.uploads.forEach { upload ->
                upload.objectKey shouldStartWith "pending/user-reports/1/"
                upload.uploadUrl.isNotBlank() shouldBe true
            }
        }

        "발급된 객체 키는 모두 서로 다르다" {
            val request = IssueImageUploadUrlsRequest(
                files = List(3) { ImageUploadFileRequest(contentType = "image/png", contentLength = 1024L) },
            )

            val result = userReportService.issueImageUploadUrls(memberId = 1L, request = request)

            result.uploads.map { it.objectKey }.toSet().size shouldBe 3
        }

    }

    "신고 접수" - {

        "정상 접수하면 RECEIVED 상태의 신고가 저장된다" {
            val reporter = saveActiveMember("신고자")
            val reported = saveActiveMember("피신고자")

            val result = userReportService.submitReport(
                reporterId = reporter.id,
                request = CreateUserReportRequest(
                    reportedMemberId = reported.id,
                    reason = "inappropriate-behavior",
                    source = "profile",
                    detail = "대화 중 폭언을 했습니다.",
                ),
            )

            val saved = memberReportRepository.findById(result.id).orElseThrow()
            saved.reporterId shouldBe reporter.id
            saved.reportedMemberId shouldBe reported.id
            saved.status shouldBe MemberReportStatus.RECEIVED
        }

        "이미지를 첨부하면 확정 영역 키로 이동해 순서대로 저장한다" {
            val reporter = saveActiveMember("신고자")
            val reported = saveActiveMember("피신고자")
            val imageKeys = issueUploadedKeys(reporter.id, 2)

            val result = userReportService.submitReport(
                reporterId = reporter.id,
                request = CreateUserReportRequest(
                    reportedMemberId = reported.id,
                    reason = "money-demand",
                    source = "match-result",
                    imageKeys = imageKeys,
                ),
            )

            val images = memberReportImageRepository.findAllByMemberReportIdOrderByDisplayOrder(result.id)
            images.size shouldBe 2
            images.forEachIndexed { index, image ->
                image.displayOrder shouldBe index
                image.objectKey shouldStartWith "user-reports/${reporter.id}/"
            }
        }

        "자기 자신을 신고하면 거부한다" {
            val reporter = saveActiveMember("신고자")

            val exception = shouldThrow<WarnException> {
                userReportService.submitReport(
                    reporterId = reporter.id,
                    request = CreateUserReportRequest(
                        reportedMemberId = reporter.id,
                        reason = "inappropriate-behavior",
                        source = "profile",
                    ),
                )
            }

            exception.errorCode shouldBe ErrorCode.CANNOT_REPORT_SELF
        }

        "존재하지 않는 회원을 신고하면 거부한다" {
            val reporter = saveActiveMember("신고자")

            val exception = shouldThrow<WarnException> {
                userReportService.submitReport(
                    reporterId = reporter.id,
                    request = CreateUserReportRequest(
                        reportedMemberId = 99999L,
                        reason = "inappropriate-behavior",
                        source = "profile",
                    ),
                )
            }

            exception.errorCode shouldBe ErrorCode.NOT_FOUND
        }

        "기타 사유는 상세 설명이 없으면 거부한다" {
            val reporter = saveActiveMember("신고자")
            val reported = saveActiveMember("피신고자")

            val exception = shouldThrow<WarnException> {
                userReportService.submitReport(
                    reporterId = reporter.id,
                    request = CreateUserReportRequest(
                        reportedMemberId = reported.id,
                        reason = "etc",
                        source = "profile",
                    ),
                )
            }

            exception.errorCode shouldBe ErrorCode.REPORT_ETC_REASON_REQUIRED
        }

        "검토 대기 중인 동일 대상 재신고는 거부한다" {
            val reporter = saveActiveMember("신고자")
            val reported = saveActiveMember("피신고자")
            memberReportRepository.save(
                MemberReportFixture.create(reporterId = reporter.id, reportedMemberId = reported.id),
            )

            val exception = shouldThrow<WarnException> {
                userReportService.submitReport(
                    reporterId = reporter.id,
                    request = CreateUserReportRequest(
                        reportedMemberId = reported.id,
                        reason = "inappropriate-behavior",
                        source = "profile",
                    ),
                )
            }

            exception.errorCode shouldBe ErrorCode.DUPLICATE_REPORT
        }

        "잘못된 사유 code는 거부한다" {
            val reporter = saveActiveMember("신고자")
            val reported = saveActiveMember("피신고자")

            val exception = shouldThrow<WarnException> {
                userReportService.submitReport(
                    reporterId = reporter.id,
                    request = CreateUserReportRequest(
                        reportedMemberId = reported.id,
                        reason = "unknown-reason",
                        source = "profile",
                    ),
                )
            }

            exception.errorCode shouldBe ErrorCode.BAD_REQUEST
        }

        "이미지가 최대 장수를 초과하면 거부한다" {
            val reporter = saveActiveMember("신고자")
            val reported = saveActiveMember("피신고자")
            val imageKeys = List(4) { index -> "pending/user-reports/${reporter.id}/key-$index" }

            val exception = shouldThrow<WarnException> {
                userReportService.submitReport(
                    reporterId = reporter.id,
                    request = CreateUserReportRequest(
                        reportedMemberId = reported.id,
                        reason = "inappropriate-behavior",
                        source = "profile",
                        imageKeys = imageKeys,
                    ),
                )
            }

            exception.errorCode shouldBe ErrorCode.REPORT_IMAGE_LIMIT_EXCEEDED
        }

        "같은 이미지 키를 중복 첨부하면 거부한다" {
            val reporter = saveActiveMember("신고자")
            val reported = saveActiveMember("피신고자")
            val duplicatedKey = issueUploadedKeys(reporter.id, 1).first()

            val exception = shouldThrow<WarnException> {
                userReportService.submitReport(
                    reporterId = reporter.id,
                    request = CreateUserReportRequest(
                        reportedMemberId = reported.id,
                        reason = "inappropriate-behavior",
                        source = "profile",
                        imageKeys = listOf(duplicatedKey, duplicatedKey),
                    ),
                )
            }

            exception.errorCode shouldBe ErrorCode.INVALID_REPORT_IMAGE_KEY
        }

        "발급받지 않은 이미지 키는 거부한다" {
            val reporter = saveActiveMember("신고자")
            val reported = saveActiveMember("피신고자")

            val exception = shouldThrow<WarnException> {
                userReportService.submitReport(
                    reporterId = reporter.id,
                    request = CreateUserReportRequest(
                        reportedMemberId = reported.id,
                        reason = "inappropriate-behavior",
                        source = "profile",
                        imageKeys = listOf("pending/user-reports/${reporter.id}/forged-key"),
                    ),
                )
            }

            exception.errorCode shouldBe ErrorCode.INVALID_REPORT_IMAGE_KEY
        }

        "다른 회원이 발급받은 이미지 키는 거부한다" {
            val reporter = saveActiveMember("신고자")
            val reported = saveActiveMember("피신고자")
            val other = saveActiveMember("다른회원")
            val otherKeys = issueUploadedKeys(other.id, 1)

            val exception = shouldThrow<WarnException> {
                userReportService.submitReport(
                    reporterId = reporter.id,
                    request = CreateUserReportRequest(
                        reportedMemberId = reported.id,
                        reason = "inappropriate-behavior",
                        source = "profile",
                        imageKeys = otherKeys,
                    ),
                )
            }

            exception.errorCode shouldBe ErrorCode.INVALID_REPORT_IMAGE_KEY
        }
    }
})
