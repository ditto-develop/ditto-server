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

    // 아래 두 단계는 결정적이라, 어드민이 후보가 없는 이유를 다시 계산할 때도 그대로 쓴다.
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

    fun selectTopRatio(scoredDuos: List<ScoredMatch>): List<ScoredMatch> =
        TopRatioSelector.select(scoredDuos, TOP_RATIO)

    /**
     * 동점 무작위와 상관없이 1인 제한을 반드시 통과하는 페어를 가진 회원. 저장된 후보가 없는데 여기 들면
     * 매칭 뒤에 상태가 바뀐 것이다. 두 사람 모두에게서 [surelyKeptDuos]에 드는 페어만 반드시 살아남는다.
     */
    fun memberIdsCertainToKeepCandidate(selectedDuos: List<ScoredMatch>): Set<Long> {
        val selectedDuosByMemberId = buildMap<Long, MutableList<ScoredMatch>> {
            selectedDuos.forEach { duo -> duo.memberIds.forEach { getOrPut(it) { mutableListOf() }.add(duo) } }
        }
        val surelyKeptDuosByMemberId = selectedDuosByMemberId.mapValues { (_, duos) -> surelyKeptDuos(duos) }
        return selectedDuos
            .filter { duo -> duo.memberIds.all { duo in surelyKeptDuosByMemberId.getValue(it) } }
            .flatMap { it.memberIds }
            .toSet()
    }

    // 제한 이하면 전부 남는다. 넘으면 제한+1 번째 점수보다 엄격히 높은 페어만 섞는 순서와 상관없이 위에 남는다.
    private fun surelyKeptDuos(duos: List<ScoredMatch>): Set<ScoredMatch> {
        if (duos.size <= PICKS_PER_MEMBER) return duos.toSet()

        val firstDroppableScore = duos.sortedByDescending { it.score }[PICKS_PER_MEMBER].score
        return duos.filter { it.score > firstDroppableScore }.toSet()
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
        private const val TOP_RATIO = 0.2 // 상위 20% 선발
        private const val PICKS_PER_MEMBER = 5
        const val MAX_AGE_GAP = 10 // 나이차 10 초과 페어 제외
    }
}
