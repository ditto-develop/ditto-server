package com.ditto.domain.member.entity

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException

/**
 * 회원 거주지(사는곳). 시/도 단위, 회원당 1개.
 *
 * - [code]: FE/클라이언트와 주고받는 식별자 (kebab-case). API 계층에서 [from]으로 매핑한다.
 * - [description]: 화면 표시용 라벨
 */
enum class Location(
    val code: String,
    private val description: String,
) {
    SEOUL("seoul", "서울"),
    GYEONGGI("gyeonggi", "경기"),
    INCHEON("incheon", "인천"),
    BUSAN("busan", "부산"),
    DAEGU("daegu", "대구"),
    DAEJEON("daejeon", "대전"),
    GWANGJU("gwangju", "광주"),
    ULSAN("ulsan", "울산"),
    SEJONG("sejong", "세종"),
    GANGWON("gangwon", "강원"),
    CHUNGBUK("chungbuk", "충북"),
    CHUNGNAM("chungnam", "충남"),
    JEONBUK("jeonbuk", "전북"),
    JEONNAM("jeonnam", "전남"),
    GYEONGBUK("gyeongbuk", "경북"),
    GYEONGNAM("gyeongnam", "경남"),
    JEJU("jeju", "제주"),
    ;

    companion object {
        fun from(code: String): Location =
            entries.firstOrNull { it.code == code }
                ?: throw WarnException(ErrorCode.BAD_REQUEST)
    }
}
