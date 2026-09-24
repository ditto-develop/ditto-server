package com.ditto.api.notification.message

import com.ditto.domain.chat.entity.ChatMessageType
import com.ditto.domain.notification.entity.NotificationType

/**
 * 알림 문구를 한곳에 모은다. 정본은 기획의 "알림 문구" 표고, 문구를 바꿀 때 여기만 고친다.
 *
 * **문구는 발송 시점에 확정해 저장한다**(`Notification` KDoc). 그래서 이 객체는 순수 함수 모음이고
 * 조회 경로에서는 쓰이지 않는다.
 */
object NotificationMessages {

    /** 이번 주 매칭 후보가 생겼다. */
    fun matchResult(): NotificationContent = NotificationContent(
        type = NotificationType.MATCH_RESULT,
        title = "같은 답을 한 사람을 찾았어요",
        body = "누구인지 확인해보세요.",
    )

    /** 퀴즈를 끝냈지만 이번 주 후보가 없다. */
    fun noMatch(): NotificationContent = NotificationContent(
        type = NotificationType.NO_MATCH,
        title = "이번 주는 답이 닿지 않았어요",
        body = "다음 주에 새로운 질문으로 다시 찾아볼게요.",
    )

    /** 그룹이 인원을 채웠다. [memberCount]는 방에 모인 사람 수(나 포함)다. */
    fun groupFormed(memberCount: Int): NotificationContent = NotificationContent(
        type = NotificationType.GROUP_FORMED,
        title = "그룹이 만들어졌어요",
        body = "답이 비슷한 ${memberCount}명이 모였어요. 멤버를 확인해보세요.",
    )

    /** 그룹이 인원을 채우지 못해 취소됐다. 수락까지 한 사람에게만 간다. 기획 표에 없는 알림이라 문구는 확정 전이다. */
    fun groupNotFormed(): NotificationContent = NotificationContent(
        type = NotificationType.GROUP_NOT_FORMED,
        title = "그룹이 인원 미달로 취소됐어요",
        body = "함께할 사람이 충분히 모이지 않았어요. 다음 주 퀴즈에서 새로운 그룹을 만나보세요.",
    )

    /** 재매칭이 성사돼 방이 예약됐다. */
    fun rematchMatched(counterpartNickname: String): NotificationContent = NotificationContent(
        type = NotificationType.REMATCH_MATCHED,
        title = "재매칭이 성사됐어요",
        body = "${counterpartNickname}님과 대화방이 다시 열렸어요. 이어서 이야기 나눠보세요.",
    )

    /** [requesterNickname] 이 나에게 대화를 신청했다. */
    fun matchRequested(requesterNickname: String): NotificationContent = NotificationContent(
        type = NotificationType.MATCH_REQUESTED,
        title = "${requesterNickname}님이 대화를 신청했어요",
        body = "수락하면 금요일에 대화방이 열려요.",
    )

    /** 내가 보낸 대화 신청을 [accepterNickname] 이 수락했다. */
    fun matchAccepted(accepterNickname: String): NotificationContent = NotificationContent(
        type = NotificationType.MATCH_ACCEPTED,
        title = "${accepterNickname}님이 대화 신청을 수락했어요",
        body = "금요일에 설레는 만남이 시작돼요!",
    )

    /**
     * 내가 보낸 대화 신청을 [rejecterNickname] 이 거절했다.
     *
     * 거절한 사람을 밝히는 이유: 한 주에 여러 명에게 신청할 수 있어 누구인지 없으면 알림이 쓸모없다.
     */
    fun matchRejected(rejecterNickname: String): NotificationContent = NotificationContent(
        type = NotificationType.MATCH_REJECTED,
        title = "${rejecterNickname}님이 대화 신청을 거절했어요",
        body = "새로운 인연에게 대화를 신청해보세요.",
    )

    /**
     * 채팅이 끝나 평가가 열렸다.
     *
     * 그룹은 상대가 여럿이라 이름을 하나만 쓸 수 없으므로 인원으로 말한다.
     * 표의 문구("{닉네임}님과의 만남을 짧게 기록해주세요")는 1:1 기준이다.
     */
    fun reviewRequest(counterpartNicknames: List<String>): NotificationContent = NotificationContent(
        type = NotificationType.REVIEW_REQUEST,
        title = "이번 만남은 어땠어요?",
        body = when (counterpartNicknames.size) {
            0 -> "지난 만남을 짧게 기록해주세요. 다음 매칭이 더 잘 맞아요."
            1 -> "${counterpartNicknames.first()}님과의 만남을 짧게 기록해주세요. 다음 매칭이 더 잘 맞아요."
            else -> "함께한 ${counterpartNicknames.size}명과의 만남을 짧게 기록해주세요. 다음 매칭이 더 잘 맞아요."
        },
    )

    /** 만남 투표가 시작됐다. 투표에는 마감 시각이 없어서(vote.md) 표의 {마감}은 방 종료 시각으로 채운다. */
    fun voteCreated(): NotificationContent = NotificationContent(
        type = NotificationType.VOTE_CREATED,
        title = "만남 투표가 시작됐어요",
        body = "언제 어디서 만날지 정해요. 일요일 자정까지 투표할 수 있어요.",
    )

    /** 만남 투표가 마감됐다. */
    fun voteClosed(): NotificationContent = NotificationContent(
        type = NotificationType.VOTE_CLOSED,
        title = "만남 투표가 끝났어요",
        body = "언제 어디서 만날지 확인해보세요.",
    )

    /** 채팅방이 열려 대화를 시작할 수 있다. 종료는 일요일 자정이다(`ChatPeriod`). */
    fun chatRoomOpened(): NotificationContent = NotificationContent(
        type = NotificationType.CHAT_ROOM_OPENED,
        title = "이제 대화를 시작할 수 있어요",
        body = "일요일 자정까지예요. 천천히 이야기 나눠보세요.",
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
        title = "${senderNickname}님의 메시지",
        body = when (messageType) {
            ChatMessageType.IMAGE -> "사진을 보냈어요."
            else -> content
        },
    )

    /** 채팅 종료가 [hoursLeft]시간 남았다. */
    fun chatEndingSoon(hoursLeft: Long): NotificationContent = NotificationContent(
        type = NotificationType.CHAT_ENDING_SOON,
        title = "대화가 ${hoursLeft}시간 뒤에 닫혀요",
        body = "아직 못다 한 이야기가 있다면 지금 해보는 건 어때요?",
    )

    // SYSTEM_NOTICE 문구는 운영이 직접 쓰므로 여기 두지 않는다. 발송 화면(어드민)이 붙을 때
    // 그 입력값으로 NotificationContent 를 만든다 — 지금은 발송 주체가 없어 만들 문구도 없다.
}
