package com.ditto.api.chat.dto

/**
 * 투표하기 요청 — 담긴 집합이 그 회원의 **최종 선택**이다(치환).
 * 재투표가 같은 엔드포인트를 다시 부르므로 append 로 구현하면 표가 계속 늘어난다.
 *
 * 빈 배열 = 해당 유형 표 취소. 현재 화면(제출 게이트가 둘 다 1개 이상)으로는 만들 수 없지만 계약은 허용한다.
 */
data class ChatVoteCastRequest(
    val placeIds: List<Long> = emptyList(),
    val timeIds: List<Long> = emptyList(),
)
