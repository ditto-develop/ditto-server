package com.ditto.api.user.dto

/**
 * 내 프로필의 "내 통계" 카드. 각 지표 정의는 피그마 `6.1 프로필` 주석에서 확정됐다.
 *
 * - [participationWeeks] 참여 주차 = 퀴즈를 완주한 운영 주 수 (같은 주에 그룹·1:1 을 둘 다 완주해도 한 주)
 * - [matchCount] 매칭 성사 = 채팅방 개설 횟수
 * - [meetingCount] 만남 횟수 = 상대방 평가에서 '만났어요'를 받은 개수
 */
data class MyStatsResponse(
    val participationWeeks: Long,
    val matchCount: Long,
    val meetingCount: Long,
)
