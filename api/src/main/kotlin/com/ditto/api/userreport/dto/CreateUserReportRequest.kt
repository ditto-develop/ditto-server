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

    /** 사유 하나(구버전 계약). [reasons]가 오면 무시한다. 둘 다 없으면 거부한다. */
    val reason: String? = null,

    /** 선택한 사유 code 목록(다중 선택). 대표 사유는 서버가 가장 심각한 것으로 고른다. */
    @field:Size(max = MAX_REASON_COUNT, message = "신고 사유가 너무 많습니다.")
    val reasons: List<String>? = null,

    @field:NotBlank(message = "신고 접수 위치가 필요합니다.")
    val source: String,

    @field:Size(max = MemberReport.DETAIL_MAX_LENGTH, message = "상세 설명은 최대 500자까지 가능합니다.")
    val detail: String? = null,

    @field:Size(max = MemberReportImage.MAX_COUNT, message = "이미지 첨부는 최대 3장까지 가능합니다.")
    val imageKeys: List<String> = emptyList(),

    val block: Boolean = false,
) {
    /** 다중 선택([reasons])을 우선하고, 없으면 구버전 단일 [reason]을 쓴다. 중복 code 는 하나로 친다. */
    fun reasonCodes(): Set<String> =
        (reasons ?: listOfNotNull(reason)).filter { it.isNotBlank() }.toSet()

    companion object {
        /** 사유 값 개수(5)보다 넉넉하게 — 중복 code 가 섞여도 거르기 전에 막히지 않게. */
        private const val MAX_REASON_COUNT = 10
    }
}
