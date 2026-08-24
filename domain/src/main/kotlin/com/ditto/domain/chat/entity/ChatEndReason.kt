package com.ditto.domain.chat.entity

/**
 * 채팅방이 끝난 이유. "누가" 끝냈는지는 담지 않는다 —
 * 나갈 때 남기는 SYSTEM 메시지의 `ChatMessage.senderId`가 그 사실을 들고 있고,
 * 조회자가 자기 ID와 비교해 "상대방이 종료했다"를 판별한다.
 *
 * 값은 실제로 구분해서 쓰는 사유만 둔다(값 추가 전용). 상대 차단을 동반한 종료는 차단 기능과 함께 도입한다.
 */
enum class ChatEndReason {
    /** 기한 도달로 만료 스케줄러가 마감 */
    EXPIRED,

    /** 참여자가 직접 종료 */
    USER_ENDED,

    /** 그룹 멤버 이탈로 잔여 인원이 1명이 되어 자동 해체 */
    INSUFFICIENT_MEMBERS,
}
