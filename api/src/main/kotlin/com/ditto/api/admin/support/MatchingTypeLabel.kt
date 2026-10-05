package com.ditto.api.admin.support

import com.ditto.domain.quiz.entity.MatchingType
import org.springframework.stereotype.Component

/** 어드민 화면용 매칭 방식 표기. Thymeleaf 에서 `${@matchingTypeLabel.of(matchingType)}` 로 호출. */
@Component("matchingTypeLabel")
class MatchingTypeLabel {
    fun of(matchingType: MatchingType): String = when (matchingType) {
        MatchingType.ONE_TO_ONE -> "1:1"
        MatchingType.GROUP -> "그룹"
    }
}
