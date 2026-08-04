package com.ditto.api.userreport.service

import com.ditto.api.userreport.dto.CreateUserReportRequest
import com.ditto.api.userreport.dto.CreateUserReportResponse
import com.ditto.api.userreport.dto.ImageUploadUrlResponse
import com.ditto.api.userreport.dto.ImageUploadUrlsResponse
import com.ditto.api.setting.service.MemberBlockService
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
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserReportService(
    private val memberReportRepository: MemberReportRepository,
    private val memberReportImageRepository: MemberReportImageRepository,
    private val memberRepository: MemberRepository,
    private val memberBlockService: MemberBlockService,
    private val objectStorage: ObjectStorage,
) {

    fun issueImageUploadUrls(memberId: Long, request: IssueImageUploadUrlsRequest): ImageUploadUrlsResponse {
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
        // 순수 검증(사유·위치 매핑, 자기 신고, ETC 상세)을 DB 조회 전에 끝낸다.
        val report = MemberReport.receive(
            reporterId = reporterId,
            reportedMemberId = request.reportedMemberId,
            reason = MemberReportReason.from(request.reason),
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
            objectKeys = request.imageKeys.map { it.removePrefix(PENDING_ROOT) },
        )
        validateImageOwnership(reporterId, request.imageKeys)
        memberReportImageRepository.saveAll(images)
        // S3 이동은 모든 DB 작업이 끝난 뒤 마지막에 — 이후 실패 지점이 커밋뿐이도록 좁힌다.
        moveToPermanent(request.imageKeys)
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

    /** 본인이 발급받아 실제 업로드를 마친 키만 접수할 수 있다. */
    private fun validateImageOwnership(reporterId: Long, imageKeys: List<String>) {
        imageKeys.forEach { key ->
            if (!key.startsWith(pendingPrefix(reporterId)) || !objectStorage.exists(key)) {
                throw WarnException(ErrorCode.INVALID_REPORT_IMAGE_KEY)
            }
        }
    }

    /**
     * 업로드 이미지를 확정 영역으로 옮긴다. DB 트랜잭션과 S3는 원자적으로 묶이지 않으므로,
     * 도중 실패하면 이미 옮긴 객체를 pending으로 되돌려(best-effort) 고아 객체를 막는다.
     */
    private fun moveToPermanent(imageKeys: List<String>) {
        val movedKeys = mutableListOf<String>()
        imageKeys.forEach { sourceKey ->
            runCatching {
                objectStorage.move(sourceKey, sourceKey.removePrefix(PENDING_ROOT))
            }.onFailure { exception ->
                restoreToPending(movedKeys)
                throw exception
            }
            movedKeys.add(sourceKey)
        }
    }

    private fun restoreToPending(movedKeys: List<String>) {
        movedKeys.forEach { sourceKey ->
            runCatching {
                objectStorage.move(sourceKey.removePrefix(PENDING_ROOT), sourceKey)
            }.onFailure { exception ->
                logger.warn(exception) { "신고 이미지 보상 이동 실패 — 확정 영역에 고아 객체 잔존 가능: $sourceKey" }
            }
        }
    }

    private fun pendingPrefix(memberId: Long): String = "$PENDING_ROOT$PERMANENT_KEY_PREFIX/$memberId/"

    companion object {
        private val logger = KotlinLogging.logger {}

        // 접수되지 않은 업로드는 S3 라이프사이클 규칙이 pending/ 접두사 기준으로 삭제한다.
        private const val PENDING_ROOT = "pending/"
        private const val PERMANENT_KEY_PREFIX = "user-reports"
    }
}
