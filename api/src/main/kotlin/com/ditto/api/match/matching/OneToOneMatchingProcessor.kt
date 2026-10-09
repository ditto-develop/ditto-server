package com.ditto.api.match.matching

import com.ditto.api.match.matching.OneToOneMatchingProcessor.Companion.MAX_AGE_GAP
import com.ditto.domain.quiz.entity.MatchingType
import org.springframework.stereotype.Component
import kotlin.math.abs

/**
 * 성별 선호가 서로 맞고 나이 차가 10살 이내이며 차단 관계가 아닌 페어만 점수를 매긴 뒤, 회원마다 상위 5명을 고른다.
 * 점수가 낮다고 따로 걸러내지 않아서 자격 있는 상대가 있으면 누구나 후보를 받는다.
 */
@Component
class OneToOneMatchingProcessor : MatchingProcessor {

    override val matchingType: MatchingType = MatchingType.ONE_TO_ONE

    override fun match(participants: List<MatchParticipant>): List<ScoredMatch> {
        if (participants.size < 2) return emptyList()

        return TopPicksSelector.select(scoreEligibleDuos(participants), PICKS_PER_MEMBER)
    }

    // 어드민이 후보가 없는 이유를 다시 계산할 때도 쓴다.
    fun scoreEligibleDuos(participants: List<MatchParticipant>): List<ScoredMatch> =
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

    // 세 조건 모두 대칭이라 A가 B의 자격 상대면 B도 A의 자격 상대다.
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
        private const val PICKS_PER_MEMBER = 5
        const val MAX_AGE_GAP = 10 // 나이차 10 초과 페어 제외
    }
}
