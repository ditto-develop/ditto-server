package com.ditto.domain.memberreport.converter

import com.ditto.domain.memberreport.entity.MemberReportReason
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/**
 * 신고 사유 집합을 단일 컬럼에 콤마 구분 enum 이름으로 저장한다 — 관심사(`InterestSetConverter`)와 같은 방식이다.
 *
 * 선언 순서(심각한 사유가 앞)로 직렬화해 같은 조합이면 항상 같은 값이 된다.
 * 배포된 [MemberReportReason] 이름을 바꾸면 기존 행을 읽지 못하므로 값 추가만 허용한다.
 */
@Converter
class MemberReportReasonSetConverter : AttributeConverter<Set<MemberReportReason>, String> {

    override fun convertToDatabaseColumn(attribute: Set<MemberReportReason>?): String =
        attribute.orEmpty()
            .sortedBy { it.ordinal }
            .joinToString(DELIMITER) { it.name }

    override fun convertToEntityAttribute(dbData: String?): Set<MemberReportReason> =
        dbData
            ?.split(DELIMITER)
            ?.filter { it.isNotBlank() }
            ?.map { MemberReportReason.valueOf(it.trim()) }
            ?.sortedBy { it.ordinal }
            ?.toCollection(LinkedHashSet())
            .orEmpty()

    companion object {
        private const val DELIMITER = ","
    }
}
