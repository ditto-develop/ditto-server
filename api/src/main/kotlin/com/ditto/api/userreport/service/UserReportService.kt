package com.ditto.api.userreport.service

import com.ditto.api.setting.service.MemberBlockService
import com.ditto.api.support.image.ImageUploadCategory
import com.ditto.api.support.image.ImageUploadUrlsResponse
import com.ditto.api.support.image.PendingImageStorage
import com.ditto.api.userreport.dto.CreateUserReportRequest
import com.ditto.api.userreport.dto.CreateUserReportResponse
import com.ditto.api.userreport.dto.IssueImageUploadUrlsRequest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.memberreport.entity.MemberReport
import com.ditto.domain.memberreport.entity.MemberReportImage
import com.ditto.domain.memberreport.entity.MemberReportReason
import com.ditto.domain.memberreport.entity.MemberReportSource
import com.ditto.domain.memberreport.entity.MemberReportStatus
import com.ditto.domain.memberreport.repository.MemberReportImageRepository
import com.ditto.domain.memberreport.repository.MemberReportRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserReportService(
    private val memberReportRepository: MemberReportRepository,
    private val memberReportImageRepository: MemberReportImageRepository,
    private val memberRepository: MemberRepository,
    private val memberBlockService: MemberBlockService,
    private val pendingImageStorage: PendingImageStorage,
) {

    fun issueImageUploadUrls(memberId: Long, request: IssueImageUploadUrlsRequest): ImageUploadUrlsResponse =
        pendingImageStorage.issueUploadUrls(ImageUploadCategory.USER_REPORT, memberId, request.files)

    @Transactional
    fun submitReport(reporterId: Long, request: CreateUserReportRequest): CreateUserReportResponse {
        // 순수 검증(사유·위치 매핑, 자기 신고, ETC 상세)을 DB 조회 전에 끝낸다.
        val report = MemberReport.receive(
            reporterId = reporterId,
            reportedMemberId = request.reportedMemberId,
            reasons = request.reasonCodes().map { MemberReportReason.from(it) }.toSet(),
            source = MemberReportSource.from(request.source),
            detail = request.detail,
        )

        validateReportedMemberExists(request.reportedMemberId)
        validateNotDuplicated(reporterId, request.reportedMemberId)

        // 신고 화면의 "이 사용자 차단하기"를 체크한 경우에만 차단을 만든다 — 자동 차단이 아니다.
        // 신고와 차단은 별개 기록이라, 이후 신고가 기각돼도 차단은 남는다(내 차단은 내 의사).
        if (request.block) {
            memberBlockService.block(reporterId, request.reportedMemberId)
        }

        val saved = memberReportRepository.save(report)
        val images = MemberReportImage.attachAll(
            memberReportId = saved.id,
            objectKeys = pendingImageStorage.toPermanentKeys(request.imageKeys),
        )
        pendingImageStorage.validateUploadedBy(ImageUploadCategory.USER_REPORT, reporterId, request.imageKeys)
        memberReportImageRepository.saveAll(images)
        pendingImageStorage.moveToPermanent(request.imageKeys)
        return CreateUserReportResponse(id = saved.id)
    }

    private fun validateReportedMemberExists(reportedMemberId: Long) {
        if (!memberRepository.existsById(reportedMemberId)) {
            throw WarnException(ErrorCode.NOT_FOUND)
        }
    }

    private fun validateNotDuplicated(reporterId: Long, reportedMemberId: Long) {
        val alreadyReported = memberReportRepository.existsByReporterIdAndReportedMemberIdAndStatus(
            reporterId = reporterId,
            reportedMemberId = reportedMemberId,
            status = MemberReportStatus.RECEIVED,
        )
        if (alreadyReported) {
            throw WarnException(ErrorCode.DUPLICATE_REPORT)
        }
    }
}
