package com.ditto.domain.chat.entity

/** 투표 진행 상태. 마감되면 표를 던질 수 없고 결과만 읽는다. */
enum class ChatVoteStatus {
    OPEN,
    CLOSED,
}
