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
    ;

    // 하나를 고르면 걸러진 유형의 0 행을 그리지 않게 그 유형 라벨만 내린다.
    fun quizMatchingTypeLabels(): Map<String, String> =
        shownOptions().mapNotNull { option -> option.quizMatchingType?.let { it.name to option.description } }.toMap()

    fun memberReviewMatchTypeLabels(): Map<String, String> =
        shownOptions().mapNotNull { option -> option.chatRoomType?.let { it.name to option.description } }.toMap()

    private fun shownOptions(): List<MatchingTypeFilter> = if (this == ALL) entries - ALL else listOf(this)
}
