package com.ditto.domain.member.entity

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException

/**
 * 회원 직업. 회원당 1개. (FE: occupation)
 *
 * - [code]: FE/클라이언트와 주고받는 식별자 (kebab-case). API 계층에서 [from]으로 매핑한다.
 * - [description]: 화면 표시용 라벨
 */
enum class Job(
    val code: String,
    private val description: String,
) {
    IT_TECH("it-tech", "IT/기술"),
    MANAGEMENT("management", "경영/사무"),
    MARKETING("marketing", "마케팅/광고"),
    DESIGN("design", "디자인"),
    EDUCATION("education", "교육"),
    MEDICAL("medical", "의료/보건"),
    FINANCE("finance", "금융"),
    LEGAL("legal", "법률"),
    MANUFACTURING("manufacturing", "제조/생산"),
    DISTRIBUTION("distribution", "유통/판매"),
    SERVICE("service", "서비스"),
    CONSTRUCTION("construction", "건설"),
    ARTS_MEDIA("arts-media", "예술/미디어"),
    RESEARCH("research", "연구"),
    PUBLIC_ADMIN("public-admin", "공공/행정"),
    STUDENT("student", "학생"),
    ETC("etc", "기타"),
    ;

    companion object {
        fun from(code: String): Job =
            entries.firstOrNull { it.code == code }
                ?: throw WarnException(ErrorCode.BAD_REQUEST)
    }
}
