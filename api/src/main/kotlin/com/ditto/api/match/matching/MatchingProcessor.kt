package com.ditto.api.match.matching

import com.ditto.domain.quiz.entity.MatchingType

/**
 * 매칭 프로세스 실행기.
 *
 * 매칭 타입(1:1 / 그룹)별로 구현을 분리하기 위한 확장 지점.
 * 현재는 [OneToOneMatchingProcessor] 만 존재하며, 그룹 매칭은 추후 별도 구현으로 추가한다.
 */
interface MatchingProcessor {

    /** 이 구현이 담당하는 매칭 타입 */
    val matchingType: MatchingType

    /** 참여자 답변을 바탕으로 매칭 프로세스를 실행해 결과를 계산한다. */
    fun match(context: MatchingContext): MatchingResult
}
