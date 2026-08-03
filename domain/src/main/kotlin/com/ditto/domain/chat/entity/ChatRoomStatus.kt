package com.ditto.domain.chat.entity

/**
 * 채팅방 생명주기 상태.
 *
 * `SCHEDULED → ACTIVE → ENDED` 한 방향으로만 가며 되돌아오지 않는다.
 * 재매칭 방처럼 개방 시각이 미래인 방이 `SCHEDULED`로 만들어지고, 일반 매칭 방은 개방된 채로 시작한다.
 */
enum class ChatRoomStatus {
    SCHEDULED,
    ACTIVE,
    ENDED,
}
