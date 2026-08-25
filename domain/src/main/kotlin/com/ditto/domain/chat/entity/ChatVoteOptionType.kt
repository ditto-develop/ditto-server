package com.ditto.domain.chat.entity

/**
 * 선택지 종류. 한 투표가 장소와 시간을 함께 묻는다(피그마 4.2.2 — 장소 다음 시간).
 *
 * 유형마다 채우는 필드가 다르므로([ChatVoteOption]) 생성은 유형별 팩토리로만 한다.
 */
enum class ChatVoteOptionType {
    PLACE,
    TIME,
}
