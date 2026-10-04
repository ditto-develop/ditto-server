package com.ditto.domain.notification.entity

/**
 * 알림 유형. 화면(피그마 7.2)에 찍힌 카드 종류와 1:1 이다.
 *
 * 유형이 필터 칩 분류, 아이콘, target_id 가 가리키는 대상, 눌렀을 때 갈 화면을 정한다.
 * 대상을 유형이 정하므로 targetType 컬럼은 따로 두지 않는다.
 *
 * ONCE_PER_TARGET 은 같은 대상에 한 번만 알린다는 뜻이다. 스케줄러가 매 주기 같은 방이나 퀴즈셋을
 * 다시 집어와도 알림은 하나여야 해서 적재 쪽이 존재 검사를 한다. 새 메시지는 반대로 여러 번 오고,
 * 대신 안읽은 행을 접는다.
 */
enum class NotificationType(
    val category: NotificationCategory,
    val targetDescription: String,
    val duplicatePolicy: DuplicatePolicy,
    val deepLinkTarget: DeepLinkTarget,
) {
    /**
     * 이번 주 퀴즈가 열렸다. 대상은 그 주 활성 셋 중 문항이 있는 셋의 id 최소값이다.
     * 주마다 한 번만 보내려고 셋을 대상으로 두며, 1:1과 그룹 셋이 둘 다 있어도 알림은 하나다.
     */
    QUIZ_OPENED(
        NotificationCategory.MATCHING,
        "quiz_set.id (이번 주 대표 셋)",
        DuplicatePolicy.ONCE_PER_TARGET,
        DeepLinkTarget.CURRENT_QUIZ,
    ),

    /** 이번 주 퀴즈 마감이 가깝다. 아직 끝내지 않은 활성 회원에게. 대상은 [QUIZ_OPENED]와 같은 대표 셋. */
    QUIZ_CLOSING_SOON(
        NotificationCategory.MATCHING,
        "quiz_set.id (이번 주 대표 셋)",
        DuplicatePolicy.ONCE_PER_TARGET,
        DeepLinkTarget.CURRENT_QUIZ,
    ),

    /**
     * 주간 매칭 후보가 생겼다. 대상은 퀴즈셋이다. 회원과 유형만으로 막으면 평생 한 번만 알리게 돼
     * 주마다 한 번을 셀 대상이 필요하고, 그 셋의 매칭 유형으로 1:1과 그룹 결과 화면을 고른다.
     */
    MATCH_RESULT(
        NotificationCategory.MATCHING,
        "quiz_set.id (이번 주 퀴즈셋)",
        DuplicatePolicy.ONCE_PER_TARGET,
        DeepLinkTarget.MATCHING_RESULT,
    ),

    /** 퀴즈를 끝냈지만 이번 주 후보가 없다. 수신자는 매칭 풀에 든 회원. 대상이 퀴즈셋인 이유는 [MATCH_RESULT]와 같다. */
    NO_MATCH(
        NotificationCategory.MATCHING,
        "quiz_set.id (이번 주 퀴즈셋)",
        DuplicatePolicy.ONCE_PER_TARGET,
        DeepLinkTarget.MATCHING_RESULT,
    ),

    /** 그룹 매칭이 인원을 채워 활성화됐다. */
    GROUP_FORMED(
        NotificationCategory.MATCHING,
        "chat_room.id (그룹 방)",
        DuplicatePolicy.ONCE_PER_TARGET,
        DeepLinkTarget.GROUP_CHAT_ROOM,
    ),

    /**
     * 그룹이 응답 마감까지 최소 인원을 채우지 못해 취소됐다. 방이 열리지 않아 가리킬 방이 없으므로
     * 대상은 그룹 매칭이다.
     */
    GROUP_NOT_FORMED(
        NotificationCategory.MATCHING,
        "group_match.id (미성사 그룹)",
        DuplicatePolicy.ONCE_PER_TARGET,
        DeepLinkTarget.GROUP_MATCHING,
    ),

    /** 그룹 멤버가 나와 1:1 재매칭을 원한다고 냈다. 쌍마다 한 번. 신청/수락 모델(ADR 0031). */
    REMATCH_REQUESTED(
        NotificationCategory.MATCHING,
        "rematch.id",
        DuplicatePolicy.ONCE_PER_TARGET,
        DeepLinkTarget.REMATCH_PAIR_RATING,
    ),

    /** 한쪽이 원했는데 성사되지 않았다. 원했던 쪽에게. 쌍마다 한 번. */
    REMATCH_REJECTED(
        NotificationCategory.MATCHING,
        "rematch.id",
        DuplicatePolicy.ONCE_PER_TARGET,
        DeepLinkTarget.REMATCH_PAIR_RATING,
    ),

    /** 재매칭이 성사돼 채팅방이 예약됐다. */
    REMATCH_MATCHED(
        NotificationCategory.MATCHING,
        "chat_room.id (재매칭 방)",
        DuplicatePolicy.ONCE_PER_TARGET,
        DeepLinkTarget.REMATCH_CHAT_ROOM,
    ),

    /** 상대가 나에게 1:1 대화를 신청했다. 대상이 매칭 건인 이유는 [MATCH_REJECTED]와 같다. */
    MATCH_REQUESTED(
        NotificationCategory.MATCHING,
        "personal_match.id",
        DuplicatePolicy.ONCE_PER_TARGET,
        DeepLinkTarget.ONE_TO_ONE_MATCHING,
    ),

    /** 내가 보낸 1:1 대화 신청을 상대가 수락했다. 대상이 매칭 건인 이유는 [MATCH_REJECTED]와 같다. */
    MATCH_ACCEPTED(
        NotificationCategory.MATCHING,
        "personal_match.id",
        DuplicatePolicy.ONCE_PER_TARGET,
        DeepLinkTarget.ONE_TO_ONE_MATCHING,
    ),

    /**
     * 내가 보낸 1:1 대화 신청을 상대가 거절했다. 대상이 매칭 건인 이유: 거절은 되돌릴 수 없고
     * ([PersonalMatch.reject] 가 PENDING 만 받는다) 매칭 건마다 한 번이 정확한 단위다.
     * 회원+유형으로만 막으면 그 주 이후로 영영 알리지 못한다.
     */
    MATCH_REJECTED(
        NotificationCategory.MATCHING,
        "personal_match.id",
        DuplicatePolicy.ONCE_PER_TARGET,
        DeepLinkTarget.ONE_TO_ONE_MATCHING,
    ),

    /** 채팅이 끝나 상대 평가가 열렸다. */
    REVIEW_REQUEST(
        NotificationCategory.MATCHING,
        "chat_room.id (끝난 방)",
        DuplicatePolicy.ONCE_PER_TARGET,
        DeepLinkTarget.CHAT_ROOM_RATING,
    ),

    /**
     * 열린 평가를 아직 끝내지 않았다. [REVIEW_REQUEST]와 유형을 나눈 이유: 그쪽은 대상당 1회라 같은 방으로 다시
     * 적재되지 않는다. 평가에는 마감이 없어 리마인드도 방마다 한 번이다.
     */
    REVIEW_REMINDER(
        NotificationCategory.MATCHING,
        "chat_room.id (끝난 방)",
        DuplicatePolicy.ONCE_PER_TARGET,
        DeepLinkTarget.CHAT_ROOM_RATING,
    ),

    /** 채팅방이 열려 대화를 시작할 수 있다. 방마다 한 번만 알린다. */
    CHAT_ROOM_OPENED(
        NotificationCategory.CHAT,
        "chat_room.id (열린 방)",
        DuplicatePolicy.ONCE_PER_TARGET,
        DeepLinkTarget.CHAT_ROOM,
    ),

    /** 상대가 메시지를 보냈다. 같은 방의 안읽은 알림은 접힌다. */
    CHAT_MESSAGE(
        NotificationCategory.CHAT,
        "chat_room.id",
        DuplicatePolicy.COLLAPSE_UNREAD,
        DeepLinkTarget.CHAT_ROOM,
    ),

    /** 방이 열린 뒤 한동안 아무도 말하지 않았다. 방마다 한 번만 알린다. */
    CHAT_NO_MESSAGE(
        NotificationCategory.CHAT,
        "chat_room.id",
        DuplicatePolicy.ONCE_PER_TARGET,
        DeepLinkTarget.CHAT_ROOM,
    ),

    /** 채팅 종료가 가까워졌다. 방마다 한 번만 알린다. */
    CHAT_ENDING_SOON(
        NotificationCategory.CHAT,
        "chat_room.id",
        DuplicatePolicy.ONCE_PER_TARGET,
        DeepLinkTarget.CHAT_ROOM,
    ),

    /** 만남 투표가 시작됐다. 생성이 방당 열린 투표 1개로 막혀 있어 중복을 유형이 막지 않는다. [VOTE_CLOSED]와 같다. */
    VOTE_CREATED(
        NotificationCategory.CHAT,
        "chat_room.id",
        DuplicatePolicy.ALLOW,
        DeepLinkTarget.GROUP_CHAT_ROOM,
    ),

    /**
     * 만남 투표가 마감돼 결과가 확정됐다. 중복을 유형이 막지 않는 이유: 실제 발행이 close 의
     * 멱등(실제로 닫은 요청만)으로 이미 한 번이고, 같은 방의 다음 투표 마감은 정당한 새 알림이다.
     */
    VOTE_CLOSED(
        NotificationCategory.CHAT,
        "chat_room.id",
        DuplicatePolicy.ALLOW,
        DeepLinkTarget.GROUP_CHAT_ROOM,
    ),

    /**
     * 운영 공지와 업데이트 안내. 같은 내용을 다시 보낼 수 있어야 해서 중복을 막지 않는다.
     * 대상은 공지 이력이며, 어느 공지에서 나온 알림인지 추적하는 데만 쓴다. 눌러도 이동하지 않는다.
     */
    SYSTEM_NOTICE(
        NotificationCategory.SYSTEM,
        "system_notice.id",
        DuplicatePolicy.ALLOW,
        DeepLinkTarget.NONE,
    ),

    /**
     * 내가 한 신고가 제재로 처리됐다. 신고자에게 신고 건마다 한 번 보내서, 같은 사람을 여러 번 신고했으면 건마다 알린다.
     * 제재 수위와 피신고자는 밝히지 않는다. 카테고리가 SYSTEM 인 이유는 [SANCTION_IMPOSED]와 같다.
     */
    REPORT_ACTIONED(
        NotificationCategory.SYSTEM,
        "member_report.id",
        DuplicatePolicy.ONCE_PER_TARGET,
        DeepLinkTarget.NONE,
    ),

    /**
     * 신고로 제재를 받았다. 피신고자에게. 제재마다 한 번. 운영이 보내는 계정 안내라 수신 설정과 무관하게 나가야 해서
     * SYSTEM 이다(MATCHING·CHAT 은 토글로 꺼진다).
     */
    SANCTION_IMPOSED(
        NotificationCategory.SYSTEM,
        "sanction.id",
        DuplicatePolicy.ONCE_PER_TARGET,
        DeepLinkTarget.SANCTION,
    ),
    ;

    companion object {

        /** 해당 카테고리에 속한 유형들. 목록 조회의 필터 조건으로 쓰인다. */
        fun of(category: NotificationCategory): List<NotificationType> = entries.filter { it.category == category }
    }
}

/**
 * 같은 대상에 알림이 다시 생겼을 때의 처리 방식. 적재 지점이 여럿이라 부르는 쪽이 정하면
 * 같은 유형이 곳에 따라 다르게 쌓이므로 유형이 정한다.
 *
 * [ALLOW]가 아닌 정책은 판정 대상이 필요하므로 `targetId`가 있어야 한다.
 */
enum class DuplicatePolicy {

    /** 매번 새 행으로 남긴다. */
    ALLOW,

    /** `(회원, 유형, 대상)`에 이미 행이 있으면 남기지 않는다. */
    ONCE_PER_TARGET,

    /** 같은 `(회원, 유형, 대상)`의 안읽은 행을 지우고 새로 남겨 목록에 한 줄만 보이게 한다. */
    COLLAPSE_UNREAD,
}
