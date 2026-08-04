package com.ditto.api.user.dto

/**
 * 내 프로필의 "내 통계" 카드. 각 지표 정의는 피그마 `6.1 프로필` 주석에서 확정됐다.
 *
 * - [participationWeeks] 참여 주차 = 퀴즈 참여(완주) 횟수
 * - [matchCount] 매칭 성사 = 채팅방 개설 횟수
 * - [meetingCount] 만남 횟수 = 상대방 평가에서 '만났어요'를 받은 개수
 */
data class MyStatsResponse(
    val participationWeeks: Long,
    val matchCount: Long,
    val meetingCount: Long,
)
