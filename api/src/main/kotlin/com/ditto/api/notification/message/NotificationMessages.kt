package com.ditto.api.notification.message

import com.ditto.domain.chat.entity.ChatMessageType
import com.ditto.domain.notification.entity.NotificationType

/**
 * 알림 문구를 한곳에 모은다. 화면(피그마 7.2)에 찍힌 문구를 그대로 옮긴 것이며,
 * 정본은 기획의 "알림 문구 정책" 문서다 — 문구를 바꿀 때 여기만 고친다.
 *
 * **문구는 발송 시점에 확정해 저장한다**(`Notification` KDoc). 그래서 이 객체는 순수 함수 모음이고
 * 조회 경로에서는 쓰이지 않는다.
 */
object NotificationMessages {

    /** 이번 주 매칭 후보가 생겼다. */
    fun matchResult(): NotificationContent = NotificationContent(
        type = NotificationType.MATCH_RESULT,
        title = "이번 주 매칭 결과가 나왔어요",
        body = "나와 답변이 비슷한 사람들을 찾았어요. 지금 확인해 보세요.",
    )

    /** 그룹이 인원을 채웠다. [memberCount]는 방에 모인 사람 수(나 포함)다. */
    fun groupFormed(memberCount: Int): NotificationContent = NotificationContent(
        type = NotificationType.GROUP_FORMED,
        title = "그룹이 구성됐어요",
        body = "같은 취미, 취향 그룹에 ${memberCount}명이 모였어요. 지금 바로 멤버를 확인해 보세요.",
    )

    /** 재매칭이 성사돼 방이 예약됐다. */
    fun rematchMatched(counterpartNickname: String): NotificationContent = NotificationContent(
        type = NotificationType.REMATCH_MATCHED,
        title = "재매칭이 성사됐어요",
        body = "${counterpartNickname}님과의 채팅방이 다시 열렸어요.",
    )

    /**
     * 채팅이 끝나 평가가 열렸다.
     *
     * 그룹은 상대가 여럿이라 이름을 하나만 쓸 수 없으므로 인원으로 말한다 —
     * 화면 문구("{상대}님과의 만남을 평가해주세요")는 1:1 기준이다.
     */
    fun reviewRequest(counterpartNicknames: List<String>): NotificationContent = NotificationContent(
        type = NotificationType.REVIEW_REQUEST,
        title = "이번 만남은 어떠셨나요?",
        body = when (counterpartNicknames.size) {
            0 -> "지난 만남을 평가해주세요."
            1 -> "${counterpartNicknames.first()}님과의 만남을 평가해주세요."
            else -> "함께한 ${counterpartNicknames.size}명과의 만남을 평가해주세요."
        },
    )

    /**
     * 상대가 메시지를 보냈다. 본문은 미리보기다 — 이미지는 내용을 문구로 대신한다
     * (본문에 S3 key 가 그대로 들어가면 안 된다).
     */
    fun chatMessage(
        senderNickname: String,
        messageType: ChatMessageType,
        content: String,
    ): NotificationContent = NotificationContent(
        type = NotificationType.CHAT_MESSAGE,
        title = "${senderNickname}님의 새 메시지",
        body = when (messageType) {
            ChatMessageType.IMAGE -> "사진을 보냈어요."
            else -> content
        },
    )

    /** 채팅 종료가 [hoursLeft]시간 남았다. */
    fun chatEndingSoon(hoursLeft: Long): NotificationContent = NotificationContent(
        type = NotificationType.CHAT_ENDING_SOON,
        title = "채팅이 ${hoursLeft}시간 후 종료돼요",
        body = "아직 나누고 싶은 이야기가 있다면 지금 해보는 건 어때요?",
    )

    // SYSTEM_NOTICE 문구는 운영이 직접 쓰므로 여기 두지 않는다. 발송 화면(어드민)이 붙을 때
    // 그 입력값으로 NotificationContent 를 만든다 — 지금은 발송 주체가 없어 만들 문구도 없다.
}
