package com.ditto.domain.match.entity

/**
 * 그룹 후보에 배정된 멤버의 초대 응답 상태.
 *
 * 배치가 후보 그룹을 만들 때 구성원을 [PENDING]으로 깔아두고, 각자 응답하면 [ACCEPTED]·[DECLINED]가 된다.
 * [ACCEPTED]가 성사 최소 인원에 도달해야 그룹 채팅방이 열린다.
 *
 * 거절을 이 상태로 담으므로, 퀴즈셋 단위인 `group_match_decline`으로는 표현할 수 없던
 * "3번 그룹은 거절, 5번 그룹은 수락"이 가능해진다.
 */
enum class InvitationStatus(private val description: String) {
    PENDING("후보로 배정됐고 아직 응답하지 않음"),
    ACCEPTED("수락"),
    DECLINED("거절"),
}
