package com.ditto.domain.intronote.entity

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException

/**
 * 소개노트 고정 질문. 회원은 각 질문당 답변 1개를 가진다.
 *
 * - [code]: FE/클라이언트와 주고받는 식별자 (kebab-case). API 계층에서 [from]으로 매핑한다.
 * - [text]: 질문 문구 (화면 표시용)
 *
 * 값 추가만 허용하며, 이미 배포된 값의 이름/코드 변경·삭제는 금지한다.
 */
enum class IntroQuestion(
    val code: String,
    val text: String,
) {
    TRAVEL_ITEMS("travel-items", "여행갈 때 꼭 챙겨야 하는 3가지는?"),
    WEEKEND_MORNING("weekend-morning", "주말 아침 10시, 나는 주로 뭐하고 있을까?"),
    FRIENDS_SAY("friends-say", "친구들이 나한테 제일 많이 하는 말은?"),
    STRESS_RELIEF("stress-relief", "스트레스 받을 때 나만의 해소법은?"),
    BEST_CHOICE("best-choice", "최근 1년 내 가장 잘한 선택은?"),
    HAPPIEST_MOMENT("happiest-moment", "나를 가장 행복하게 만드는 순간은?"),
    MOST_USED_APPS("most-used-apps", "요즘 내가 가장 많이 쓰는 앱 3개는?"),
    FAVORITE_TIME("favorite-time", "하루 중 가장 좋아하는 시간대는? 그때 주로 뭐해?"),
    NON_NEGOTIABLE("non-negotiable", "내가 절대 양보 못하는 것은?"),
    // 문구는 Figma 개정에 맞춰 "한 줄"로 바꿨지만 이름과 code 는 유지한다 —
    // 이름은 DB 저장값(@Enumerated STRING)이고 code 는 FE 계약이라, 바꾸면 마이그레이션과 계약 파손이 따라온다.
    ONE_WORD("one-word", "나를 한 줄로 표현한다면?"),
    ;

    companion object {
        fun from(code: String): IntroQuestion =
            entries.firstOrNull { it.code == code }
                ?: throw WarnException(ErrorCode.BAD_REQUEST)
    }
}
