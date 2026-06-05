package com.ditto.domain.member.converter

import com.ditto.domain.member.entity.Interest
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/**
 * 회원 관심사(Set<Interest>)를 단일 컬럼에 콤마 구분 문자열로 저장하기 위한 컨버터.
 *
 * - 저장: {TRAVEL, MUSIC} -> "MUSIC,TRAVEL" (정렬해 canonical 직렬화)
 * - 조회: "TRAVEL,MUSIC" -> {TRAVEL, MUSIC}
 * - 빈 집합 / null 은 빈 문자열로 직렬화한다.
 *
 * 주의: 이미 배포된 [Interest] 값의 이름 변경/삭제는 금지한다.
 * 기존 행에 남은 토큰을 [Interest.valueOf]가 해석하지 못하면 해당 회원 조회 자체가
 * 예외로 실패한다(fail-loud 정책). 값 추가만 허용한다.
 */
@Converter
class InterestSetConverter : AttributeConverter<Set<Interest>, String> {

    override fun convertToDatabaseColumn(attribute: Set<Interest>?): String =
        attribute.orEmpty()
            .map { it.name }
            .sorted() // 저장 문자열을 정렬해 동일 관심사 조합이면 항상 같은 값으로 직렬화 (canonical)
            .joinToString(DELIMITER)

    override fun convertToEntityAttribute(dbData: String?): Set<Interest> =
        dbData
            ?.split(DELIMITER)
            ?.filter { it.isNotBlank() }
            ?.map { Interest.valueOf(it.trim()) }
            ?.toSet()
            .orEmpty()

    companion object {
        private const val DELIMITER = ","
    }
}
