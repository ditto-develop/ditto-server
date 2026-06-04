package com.ditto.api.match.matching

import com.ditto.domain.quiz.entity.MatchingType
import org.springframework.stereotype.Component

/**
 * 1:1 매칭 프로세스.
 *
 * 점수화(페어별 [MatchScoreCalculator]) → 상위 비율 + 동점 선발([TopRatioSelector])
 * → 1인 제한([HardLimitApplier]) 순으로 실행한다. 각 단계는 순수 컴포넌트로 분리되어 독립 검증된다.
 *
 */
@Component
class OneToOneMatchingProcessor : MatchingProcessor {

    override val matchingType: MatchingType = MatchingType.ONE_TO_ONE

    override fun match(participants: List<MatchParticipant>): MatchingResult {
        if (participants.size < 2) return MatchingResult(emptyList())

        val scoredDuos = scoreAllDuos(participants)
        val selected = TopRatioSelector.select(scoredDuos, TOP_RATIO)
        val survivors = HardLimitApplier.apply(selected, HARD_LIMIT)
        
        return MatchingResult(survivors)
    }

    private fun scoreAllDuos(participants: List<MatchParticipant>): List<ScoredDuo> =
        participants.flatMapIndexed { index, participant ->
            participants.drop(index + 1).map { otherParticipant ->
                val matchScore = MatchScoreCalculator.calculate(participant, otherParticipant)
                ScoredDuo.of(participant.memberId, otherParticipant.memberId, matchScore)
            }
        }

    companion object {
        private const val TOP_RATIO = 0.2 // 상위 20% 선발
        private const val HARD_LIMIT = 5 // 1인 최대 노출 5명
    }
}
