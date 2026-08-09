package com.ditto.domain.chat.entity

/** 채팅방의 원본 유형. `source_id`와 짝을 이뤄 어느 테이블의 어느 행에서 나온 방인지 가리킨다. */
enum class ChatRoomType {
    PERSONAL,
    GROUP,

    /** 그룹 재매칭으로 성사된 두 사람의 1:1 방. 원본은 `rematch.id`다. */
    REMATCH,
}
