package com.ditto.domain.notification.entity

/**
 * 알림을 눌렀을 때 갈 화면의 종류. 실제 FE 경로 문자열은 api 가 만든다.
 * 경로를 정하려고 `target_id`가 가리키는 행을 읽어야 하면 [readsTargetRow]가 참이다.
 * 그 행의 종류는 [NotificationType.target]이 정한다.
 */
enum class DeepLinkTarget(val readsTargetRow: Boolean) {

    /** 그 주 매칭 결과. 1:1과 그룹 화면이 달라 퀴즈셋의 매칭 유형을 본다. */
    MATCHING_RESULT(readsTargetRow = true),

    /** 1:1 매칭 화면. 신청·수락·거절이 쓴다. */
    ONE_TO_ONE_MATCHING(readsTargetRow = false),

    /** 그룹 매칭 결과 화면. 미성사 그룹은 열 방이 없어 여기로 보낸다. */
    GROUP_MATCHING(readsTargetRow = false),

    /** 대상이 그룹 방이라 방을 읽지 않고 그룹 방 화면으로 간다. */
    GROUP_CHAT_ROOM(readsTargetRow = false),

    /** 대상이 재매칭 방이라 방을 읽지 않고 1:1 방 화면으로 간다. */
    REMATCH_CHAT_ROOM(readsTargetRow = false),

    /** 방 화면. 방 종류에 따라 그룹/1:1 화면이 갈린다. */
    CHAT_ROOM(readsTargetRow = true),

    /** 끝난 방의 평가 화면. */
    CHAT_ROOM_RATING(readsTargetRow = true),

    /** 재매칭 의사를 내는 그룹 평가 화면. 대상이 쌍이라 쌍이 나온 방을 읽는다. */
    REMATCH_PAIR_RATING(readsTargetRow = true),

    /** 이번 주 퀴즈 화면. */
    CURRENT_QUIZ(readsTargetRow = false),

    /** 제재 안내 화면. 정지·차단 회원도 열 수 있다. */
    SANCTION(readsTargetRow = false),

    /** 이동할 곳이 없다. 누르면 앱만 열린다. */
    NONE(readsTargetRow = false),
}
