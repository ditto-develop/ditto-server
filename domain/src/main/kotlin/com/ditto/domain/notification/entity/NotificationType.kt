package com.ditto.domain.notification.entity

/**
 * 알림 유형. 화면(피그마 7.2)에 찍힌 카드 종류와 1:1 이다.
 *
 * 유형은 셋을 정한다 — 필터 칩 분류([category]), 아이콘(클라이언트가 유형으로 고른다),
 * 그리고 `target_id`가 무엇을 가리키는지([targetDescription]). `target_id`의 대상을 유형이 정하므로
 * 별도의 `targetType` 컬럼을 두지 않는다.
 *
 * [ONCE_PER_TARGET]는 "같은 대상에 한 번만 알린다"는 뜻이다. 스케줄러가 매 주기 같은 방·같은 퀴즈셋을
 * 다시 집어와도 알림은 하나여야 하므로, 적재 쪽이 이 값을 보고 존재 검사를 한다.
 * 새 메시지는 반대다 — 같은 방에서 여러 번 와야 하고, 대신 안읽은 행을 접는다.
 */
enum class NotificationType(
    val category: NotificationCategory,
    val targetDescription: String,
    val duplicatePolicy: DuplicatePolicy,
) {
    /**
     * 주간 매칭 후보가 생겼다. 대상은 퀴즈셋이다 — 화면은 매칭 홈으로 보내면 되지만,
     * "주마다 한 번"을 판정할 대상이 필요하다(회원+유형만으로 막으면 평생 한 번만 알린다).
     */
    MATCH_RESULT(NotificationCategory.MATCHING, "quiz_set.id (이번 주 퀴즈셋)", DuplicatePolicy.ONCE_PER_TARGET),

    /** 그룹 매칭이 인원을 채워 활성화됐다. */
    GROUP_FORMED(NotificationCategory.MATCHING, "chat_room.id (그룹 방)", DuplicatePolicy.ONCE_PER_TARGET),

    /** 재매칭이 성사돼 채팅방이 예약됐다. */
    REMATCH_MATCHED(NotificationCategory.MATCHING, "chat_room.id (재매칭 방)", DuplicatePolicy.ONCE_PER_TARGET),

    /** 채팅이 끝나 상대 평가가 열렸다. */
    REVIEW_REQUEST(NotificationCategory.MATCHING, "chat_room.id (끝난 방)", DuplicatePolicy.ONCE_PER_TARGET),

    /** 상대가 메시지를 보냈다. 같은 방의 안읽은 알림은 접힌다. */
    CHAT_MESSAGE(NotificationCategory.CHAT, "chat_room.id", DuplicatePolicy.COLLAPSE_UNREAD),

    /** 채팅 종료가 가까워졌다. 방마다 한 번만 알린다. */
    CHAT_ENDING_SOON(NotificationCategory.CHAT, "chat_room.id", DuplicatePolicy.ONCE_PER_TARGET),

    /** 운영 공지·업데이트 안내. 같은 내용을 다시 보낼 수 있어야 하므로 중복을 막지 않는다. */
    SYSTEM_NOTICE(NotificationCategory.SYSTEM, "없음", DuplicatePolicy.ALLOW),
    ;

    companion object {

        /** 해당 카테고리에 속한 유형들. 목록 조회의 필터 조건으로 쓰인다. */
        fun of(category: NotificationCategory): List<NotificationType> = entries.filter { it.category == category }
    }
}

/**
 * 같은 대상에 알림이 다시 발생했을 때의 처리 방식. 부르는 쪽이 아니라 유형이 정한다 —
 * 적재 지점이 여섯 곳이라, 각자 판단하게 두면 같은 유형이 곳에 따라 다르게 쌓인다.
 *
 * [ALLOW]가 아닌 정책은 판정 대상이 필요하므로 `targetId`가 있어야 한다.
 */
enum class DuplicatePolicy {

    /** 매번 새 행으로 남긴다. */
    ALLOW,

    /** `(회원, 유형, 대상)`에 이미 행이 있으면 남기지 않는다. */
    ONCE_PER_TARGET,

    /** 같은 `(회원, 유형, 대상)`의 **안읽은** 행을 지우고 새로 남긴다 — 목록에 한 줄만 보이게 한다. */
    COLLAPSE_UNREAD,
}
