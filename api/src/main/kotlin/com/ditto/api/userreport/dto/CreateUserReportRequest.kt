package com.ditto.api.userreport.dto

import com.ditto.domain.memberreport.entity.MemberReport
import com.ditto.domain.memberreport.entity.MemberReportImage
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

/**
 * 신고 접수 요청. [imageKeys]는 업로드 URL 발급 API에서 받은 objectKey 목록(최대 3개).
 *
 * [block]은 신고 화면 하단의 선택 체크박스("이 사용자 차단하기")에 대응한다 —
 * 신고와 차단은 별개 기능이고, 체크했을 때만 차단이 생성된다(자동 차단이 아니다).
 */
data class CreateUserReportRequest(
    @field:Positive(message = "피신고자 ID가 올바르지 않습니다.")
    val reportedMemberId: Long,

    @field:NotBlank(message = "신고 사유를 선택해 주세요.")
    val reason: String,

    @field:NotBlank(message = "신고 접수 위치가 필요합니다.")
    val source: String,

    @field:Size(max = MemberReport.DETAIL_MAX_LENGTH, message = "상세 설명은 최대 500자까지 가능합니다.")
    val detail: String? = null,

    @field:Size(max = MemberReportImage.MAX_COUNT, message = "이미지 첨부는 최대 3장까지 가능합니다.")
    val imageKeys: List<String> = emptyList(),

    val block: Boolean = false,
)
