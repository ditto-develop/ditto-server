package com.ditto.api.match.matching

import com.ditto.api.match.matching.OneToOneMatchingProcessor.Companion.MAX_AGE_GAP
import com.ditto.domain.quiz.entity.MatchingType
import org.springframework.stereotype.Component
import kotlin.math.abs

/**
 * 1:1 매칭 프로세스.
 *
 * 매칭 자격(성별·나이) 페어만 점수화(페어별 [MatchScoreCalculator]) → 상위 비율 + 동점 선발([TopRatioSelector])
 * → 1인 제한([HardLimitApplier]) 순으로 실행한다. 각 단계는 순수 컴포넌트로 분리되어 독립 검증된다.
 *
 * 후보 풀을 만드는 이 단계에서 하드 필터를 적용한다 — 자격 미달 페어는 아예 후보가 되지 않는다.
 * - 성별: 서로의 성별 선호(이성/동성)를 모두 충족해야 함.
 * - 나이: 나이차가 [MAX_AGE_GAP] 이내여야 함.
 * 두 조건 모두 대칭이라 살아남는 페어는 항상 대칭이므로 [HardLimitApplier] 의 양방향 원칙도 그대로 보존된다.
 */
@Component
class OneToOneMatchingProcessor : MatchingProcessor {

    override val matchingType: MatchingType = MatchingType.ONE_TO_ONE

    override fun match(participants: List<MatchParticipant>): List<ScoredMatch> {
        if (participants.size < 2) return emptyList()

        val scoredDuos = scoreAllDuos(participants)
        val selected = TopRatioSelector.select(scoredDuos, TOP_RATIO)
        return HardLimitApplier.apply(selected, HARD_LIMIT)
    }

    private fun scoreAllDuos(participants: List<MatchParticipant>): List<ScoredMatch> =
        participants.flatMapIndexed { index, participant ->
            participants.drop(index + 1).mapNotNull { otherParticipant ->
                if (!isValidPair(participant, otherParticipant)) return@mapNotNull null
                ScoredMatch.duo(
                    memberAId = participant.memberId,
                    memberBId = otherParticipant.memberId,
                    matchScore = MatchScoreCalculator.calculate(participant, otherParticipant),
                )
            }
        }

    /**
     * 매칭 자격: 성별 상호호환 + 나이차 [MAX_AGE_GAP] 이내 + 차단 없음.
     * 세 조건 모두 대칭이라 양방향 원칙을 깨지 않는다.
     */
    private fun isValidPair(a: MatchParticipant, b: MatchParticipant): Boolean =
        a.isMutuallyCompatibleWith(b) &&
            isWithinAgeGap(a, b) &&
            !a.isBlockedWith(b)

    /** 나이 미상이면 나이차를 판단할 수 없으므로 자격 미달로 본다. */
    private fun isWithinAgeGap(a: MatchParticipant, b: MatchParticipant): Boolean {
        val ageA = a.age ?: return false
        val ageB = b.age ?: return false
        return abs(ageA - ageB) <= MAX_AGE_GAP
    }

    companion object {
        private const val TOP_RATIO = 0.2 // 상위 20% 선발
        private const val HARD_LIMIT = 5 // 1인 최대 노출 5명
        private const val MAX_AGE_GAP = 10 // 나이차 10 초과 페어 제외
    }
}
