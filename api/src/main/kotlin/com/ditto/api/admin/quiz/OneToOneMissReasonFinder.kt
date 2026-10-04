package com.ditto.api.admin.quiz

import com.ditto.api.admin.quiz.dto.MatchMiss
import com.ditto.api.admin.quiz.dto.MatchMissReason
import com.ditto.api.match.matching.MatchParticipant
import com.ditto.api.match.matching.OneToOneMatchingProcessor
import com.ditto.api.match.matching.ScoredMatch
import com.ditto.api.match.service.MatchmakingService
import org.springframework.stereotype.Component

/**
 * 1:1 후보가 없는 풀 참여자가 어느 단계에서 빠졌는지 지금 DB 상태로 다시 계산한다. 배치와 같은 풀·단계를 쓴다.
 * 무작위가 끼는 5명 제한은 다시 돌리지 않는다. 상위 비율 컷을 넘었는데 저장된 후보가 없으면 그 단계에서 빠진 것이다.
 */
@Component
class OneToOneMissReasonFinder(
    private val matchmakingService: MatchmakingService,
    private val oneToOneMatchingProcessor: OneToOneMatchingProcessor,
) {
    /** [memberIds] 중 풀에 든 회원의 이유만 돌려준다. 풀에 없는 회원은 제외 정책에 걸린 것이다. */
    fun findPoolMisses(quizSetId: Long, memberIds: Set<Long>): Map<Long, MatchMiss> {
        if (memberIds.isEmpty()) return emptyMap()

        val pool = matchmakingService.loadMatchingPoolParticipants(quizSetId)
        val eligibleDuos = oneToOneMatchingProcessor.scoreEligibleDuos(pool)
        val funnel = OneToOneFunnel(eligibleDuos, oneToOneMatchingProcessor.selectTopRatio(eligibleDuos))
        return pool
            .filter { it.memberId in memberIds }
            .associate { participant -> participant.memberId to funnel.missOf(participant) }
    }

    private class OneToOneFunnel(eligibleDuos: List<ScoredMatch>, selectedDuos: List<ScoredMatch>) {
        private val bestEligibleScoreByMemberId: Map<Long, Double> = bestScoreByMemberId(eligibleDuos)
        private val selectedMemberIds: Set<Long> = selectedDuos.flatMap { it.memberIds }.toSet()
        private val cutoffScore: Double? = selectedDuos.minOfOrNull { it.score }

        fun missOf(participant: MatchParticipant): MatchMiss {
            val bestScore = bestEligibleScoreByMemberId[participant.memberId]
                ?: return MatchMiss(noEligiblePairReasonOf(participant))
            if (participant.memberId !in selectedMemberIds) {
                return MatchMiss(MatchMissReason.CUT_BY_TOP_RATIO, "최고 ${format(bestScore)} < 컷 ${format(cutoffScore)}")
            }
            return MatchMiss(MatchMissReason.CUT_BY_HARD_LIMIT)
        }

        private fun noEligiblePairReasonOf(participant: MatchParticipant): MatchMissReason =
            if (participant.gender == null || participant.age == null) {
                MatchMissReason.UNKNOWN_GENDER_OR_AGE
            } else {
                MatchMissReason.NO_ELIGIBLE_PAIR
            }

        private fun bestScoreByMemberId(duos: List<ScoredMatch>): Map<Long, Double> =
            buildMap {
                duos.forEach { duo ->
                    duo.memberIds.forEach { memberId -> merge(memberId, duo.score) { current, other -> maxOf(current, other) } }
                }
            }

        private fun format(score: Double?): String = score?.let { "%.1f".format(it) } ?: "-"
    }
}
