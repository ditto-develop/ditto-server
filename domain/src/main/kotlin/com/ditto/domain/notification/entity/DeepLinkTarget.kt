package com.ditto.domain.notification.entity

/**
 * 알림을 눌렀을 때 갈 화면의 종류. 실제 FE 경로 문자열은 api 가 만든다.
 * 경로를 정하려고 다른 행을 읽어야 하면 [lookup]이 그 대상을 알려 준다.
 */
enum class DeepLinkTarget(val lookup: DeepLinkLookup) {

    /** 그 주 매칭 결과. 1:1과 그룹 화면이 달라 퀴즈셋의 매칭 유형을 본다. */
    MATCHING_RESULT(DeepLinkLookup.QUIZ_SET),

    /** 1:1 매칭 화면. 신청·수락·거절이 쓴다. */
    ONE_TO_ONE_MATCHING(DeepLinkLookup.NONE),

    /** 그룹 매칭 결과 화면. 미성사 그룹은 열 방이 없어 여기로 보낸다. */
    GROUP_MATCHING(DeepLinkLookup.NONE),

    /** 대상이 그룹 방이라 방을 읽지 않고 그룹 방 화면으로 간다. */
    GROUP_CHAT_ROOM(DeepLinkLookup.NONE),

    /** 대상이 재매칭 방이라 방을 읽지 않고 1:1 방 화면으로 간다. */
    REMATCH_CHAT_ROOM(DeepLinkLookup.NONE),

    /** 방 화면. 방 종류에 따라 그룹/1:1 화면이 갈린다. */
    CHAT_ROOM(DeepLinkLookup.CHAT_ROOM),

    /** 끝난 방의 평가 화면. */
    CHAT_ROOM_RATING(DeepLinkLookup.CHAT_ROOM),

    /** 재매칭 의사를 내는 그룹 평가 화면. 대상이 쌍이라 쌍이 나온 방을 읽는다. */
    REMATCH_PAIR_RATING(DeepLinkLookup.REMATCH),

    /** 이번 주 퀴즈 화면. */
    CURRENT_QUIZ(DeepLinkLookup.NONE),

    /** 제재 안내 화면. 정지·차단 회원도 열 수 있다. */
    SANCTION(DeepLinkLookup.NONE),

    /** 이동할 곳이 없다. 누르면 앱만 열린다. */
    NONE(DeepLinkLookup.NONE),
}

/** 경로를 정하려고 읽어야 하는 대상. `target_id`가 가리키는 행이다. */
enum class DeepLinkLookup { NONE, CHAT_ROOM, QUIZ_SET, REMATCH }
