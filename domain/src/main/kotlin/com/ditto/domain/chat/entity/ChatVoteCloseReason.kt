package com.ditto.domain.chat.entity

/**
 * 투표가 마감된 이유. 마감자([ChatVote.closedBy])의 null 을 "시스템 마감"으로 해석하게 두지 않고
 * 사유를 명시한다 — 채팅방의 [ChatEndReason]과 같은 결이다.
 */
enum class ChatVoteCloseReason {
    /** 방 멤버가 직접 마감 — [ChatVote.closedBy]가 그 회원이다 */
    MEMBER,

    /** 방이 끝나며(만료·해체) 함께 마감 — 마감자가 없다 */
    ROOM_ENDED,
}
