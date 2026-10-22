package com.ditto.api.admin.stats

import com.ditto.domain.chat.entity.ChatRoomType
import com.ditto.domain.quiz.entity.MatchingType

/** 퀴즈셋은 MatchingType, 평가는 채팅방 유형으로 같은 구분을 저장한다. */
enum class MatchingTypeFilter(
    val description: String,
    val quizMatchingType: MatchingType?,
    val chatRoomType: ChatRoomType?,
) {
    ALL("전체", null, null),
    ONE_TO_ONE("1:1", MatchingType.ONE_TO_ONE, ChatRoomType.PERSONAL),
    GROUP("그룹", MatchingType.GROUP, ChatRoomType.GROUP),
}
