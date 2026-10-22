package com.ditto.api.admin.stats

import com.ditto.domain.quiz.entity.MatchingType

enum class MatchingTypeFilter(
    val description: String,
    val targetType: MatchingType?,
) {
    ALL("전체", null),
    ONE_TO_ONE("1:1", MatchingType.ONE_TO_ONE),
    GROUP("그룹", MatchingType.GROUP),
}
