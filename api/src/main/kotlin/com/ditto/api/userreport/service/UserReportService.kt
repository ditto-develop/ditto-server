package com.ditto.api.userreport.service

import com.ditto.api.system.ServerTimeProvider
import com.ditto.api.userreport.dto.CreateUserReportRequest
import com.ditto.api.userreport.dto.CreateUserReportResponse
import com.ditto.api.userreport.dto.ImageUploadUrlResponse
import com.ditto.api.userreport.dto.ImageUploadUrlsResponse
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
import com.ditto.infrastructure.storage.ObjectStorage
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserReportService(
    private val memberReportRepository: MemberReportRepository,
    private val memberReportImageRepository: MemberReportImageRepository,
    private val memberRepository: MemberRepository,
    private val objectStorage: ObjectStorage,
    private val serverTimeProvider: ServerTimeProvider,
) {

    fun issueImageUploadUrls(memberId: Long, request: IssueImageUploadUrlsRequest): ImageUploadUrlsResponse {
        if (request.files.size > MemberReportImage.MAX_COUNT) {
            throw WarnException(ErrorCode.REPORT_IMAGE_LIMIT_EXCEEDED)
        }
        val uploads = request.files.map { file ->
            val objectKey = "${pendingPrefix(memberId)}${UUID.randomUUID()}"
            ImageUploadUrlResponse(
                objectKey = objectKey,
                uploadUrl = objectStorage.issueUploadUrl(objectKey, file.contentType, file.contentLength),
            )
        }
        return ImageUploadUrlsResponse(uploads = uploads)
    }

    @Transactional
    fun submitReport(reporterId: Long, request: CreateUserReportRequest): CreateUserReportResponse {
        val reason = MemberReportReason.from(request.reason)
        val source = MemberReportSource.from(request.source)

        validateReportedMemberExists(request.reportedMemberId)
        validateNotDuplicated(reporterId, request.reportedMemberId)
        validateDailyLimit(reporterId)
        validateImageKeys(reporterId, request.imageKeys)

        val report = memberReportRepository.save(
            MemberReport.receive(
                reporterId = reporterId,
                reportedMemberId = request.reportedMemberId,
                reason = reason,
                source = source,
                detail = request.detail,
            ),
        )
        attachImages(report.id, request.imageKeys)
        return CreateUserReportResponse(id = report.id)
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

    private fun validateDailyLimit(reporterId: Long) {
        val startOfToday = serverTimeProvider.now().toLocalDate().atStartOfDay()
        val todayCount = memberReportRepository.countByReporterIdAndCreatedAtGreaterThanEqual(reporterId, startOfToday)
        if (todayCount >= DAILY_REPORT_LIMIT) {
            throw WarnException(ErrorCode.DAILY_REPORT_LIMIT_EXCEEDED)
        }
    }

    private fun validateImageKeys(reporterId: Long, imageKeys: List<String>) {
        if (imageKeys.size > MemberReportImage.MAX_COUNT) {
            throw WarnException(ErrorCode.REPORT_IMAGE_LIMIT_EXCEEDED)
        }
        if (imageKeys.size != imageKeys.distinct().size) {
            throw WarnException(ErrorCode.INVALID_REPORT_IMAGE_KEY)
        }
        imageKeys.forEach { key ->
            if (!key.startsWith(pendingPrefix(reporterId)) || !objectStorage.exists(key)) {
                throw WarnException(ErrorCode.INVALID_REPORT_IMAGE_KEY)
            }
        }
    }

    /** 검증이 끝난 이미지를 확정 영역으로 옮기고 신고에 연결한다. */
    private fun attachImages(reportId: Long, imageKeys: List<String>) {
        val images = imageKeys.mapIndexed { index, sourceKey ->
            val permanentKey = sourceKey.removePrefix(PENDING_ROOT)
            objectStorage.move(sourceKey, permanentKey)
            MemberReportImage.attach(
                memberReportId = reportId,
                objectKey = permanentKey,
                displayOrder = index,
            )
        }
        memberReportImageRepository.saveAll(images)
    }

    private fun pendingPrefix(memberId: Long): String = "$PENDING_ROOT$PERMANENT_KEY_PREFIX/$memberId/"

    companion object {
        // 접수되지 않은 업로드는 S3 라이프사이클 규칙이 pending/ 접두사 기준으로 삭제한다.
        private const val PENDING_ROOT = "pending/"
        private const val PERMANENT_KEY_PREFIX = "user-reports"
        // 어드민 검토 큐 마비·집단 신고를 완화하기 위한 회원당 일일 접수 상한
        private const val DAILY_REPORT_LIMIT = 5
    }
}
