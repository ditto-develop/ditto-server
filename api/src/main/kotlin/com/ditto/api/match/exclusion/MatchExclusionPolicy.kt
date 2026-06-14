package com.ditto.api.match.exclusion

import com.ditto.domain.quiz.entity.MatchingType

/**
 * 매칭 타입(1:1 / 그룹)별 제외 대상 회원을 결정하는 정책.
 */
interface MatchExclusionPolicy {

    val matchingType: MatchingType

    /** [participantIds] 중 제외해야 할 회원 ID 집합 */
    fun excludedMemberIds(quizSetId: Long, participantIds: Set<Long>): Set<Long>
}
