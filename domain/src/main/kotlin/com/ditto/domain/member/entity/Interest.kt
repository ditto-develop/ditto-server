package com.ditto.domain.member.entity

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException

/**
 * 회원 관심사. 회원은 여러 개를 가질 수 있다.
 *
 * - [code]: FE/클라이언트와 주고받는 식별자 (kebab-case). API 계층에서 [from]으로 매핑한다.
 * - [description]: 화면 표시용 라벨
 *
 * DB에는 별도 테이블 없이 enum 이름(name)을 콤마 구분 문자열로 저장한다.
 * 값 추가만 허용하며, 이미 배포된 값의 이름 변경/삭제는 금지한다.
 */
enum class Interest(
    val code: String,
    private val description: String,
) {
    WORKOUT("workout", "운동"),
    MOVIE_DRAMA("movie-drama", "영화/드라마"),
    // 프로필 수정 화면(피그마 6.1.1)에만 있던 칩. 온보딩 목록에는 없어 뒤늦게 추가됐다.
    EXHIBITION("exhibition", "전시"),
    PERFORMANCE("performance", "공연"),
    PHOTOGRAPHY("photography", "사진"),
    READING("reading", "독서"),
    MUSIC("music", "음악"),
    COOKING("cooking", "요리"),
    TRAVEL("travel", "여행"),
    GAMING("gaming", "게임"),
    FINANCE("finance", "재테크"),
    SELF_IMPROVEMENT("self-improvement", "자기계발"),
    PETS("pets", "반려동물"),
    ETC("etc", "기타"),
    ;

    companion object {
        fun from(code: String): Interest =
            entries.firstOrNull { it.code == code }
                ?: throw WarnException(ErrorCode.BAD_REQUEST)
    }
}
