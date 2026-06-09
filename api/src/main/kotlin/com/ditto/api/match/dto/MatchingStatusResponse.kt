package com.ditto.api.match.dto

/**
 * 매칭 상태 조회 응답.
 * 1:1 보낸/받은 요청 목록과 수락 여부, 그룹 매칭 참여/거절 상태를 함께 담는다.
 */
data class MatchingStatusResponse(
    val quizSetId: Long,
    val sentRequests: List<PersonalMatchResponse>,
    val receivedRequests: List<PersonalMatchResponse>,
    /** ACCEPTED 1:1 매칭 보유 여부 */
    val hasAcceptedMatch: Boolean,
    /** 수락된 매칭 상대 회원 ID. 없으면 null */
    val acceptedMatchUserId: Long?,
    /** 그룹 매칭 거절 여부 */
    val groupDeclined: Boolean,
    /** 활성화된(참가자 3명 이상) 그룹 방 참여 여부 */
    val groupJoined: Boolean,
    /** 그룹 방에 참여했으나 아직 비활성(인원 대기) 여부 */
    val groupJoinPending: Boolean,
)
