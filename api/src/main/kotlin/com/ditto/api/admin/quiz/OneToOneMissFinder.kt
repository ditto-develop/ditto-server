package com.ditto.api.admin.quiz

import com.ditto.api.admin.quiz.dto.MatchMiss
import com.ditto.api.admin.quiz.dto.MatchMissReason
import com.ditto.api.match.matching.MatchParticipant
import com.ditto.api.match.matching.OneToOneMatchingProcessor
import com.ditto.api.match.matching.ScoredMatch
import com.ditto.api.match.service.MatchmakingService
import com.ditto.domain.member.entity.Member
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 1:1 후보가 없는 참여자가 어느 단계에서 빠졌는지 지금 DB 상태로 다시 계산한다. 단계 순서는 이 클래스 한 곳에서 정한다.
 * 풀은 배치와 같은 계산에서 매칭 뒤에 완주한 사람을 뺀다. 그 사람들이 섞이면 컷 점수가 바뀌어 원래 참여자의 이유가 흔들린다.
 * 무작위가 끼는 1인 제한은 다시 돌리지 않는다.
 */
@Component
class OneToOneMissFinder(
    private val matchmakingService: MatchmakingService,
    private val oneToOneMatchingProcessor: OneToOneMatchingProcessor,
) {
    fun findMisses(input: OneToOneMissInput): Map<Long, MatchMiss> {
        val withoutCandidate = input.source.progresses.filter { it.memberId !in input.candidateOwnerIds }
        val prePoolMissByMemberId = buildMap {
            withoutCandidate.forEach { progress ->
                val miss = PrePoolMiss.of(progress, input.source.membersById[progress.memberId], input.generatedAt)
                if (miss != null) put(progress.memberId, miss)
            }
        }
        val poolEntrantIds = withoutCandidate.map { it.memberId }.toSet() - prePoolMissByMemberId.keys
        if (poolEntrantIds.isEmpty()) return prePoolMissByMemberId

        val funnel = OneToOneFunnel(loadPoolAtGeneration(input), oneToOneMatchingProcessor)
        val poolMissByMemberId = poolEntrantIds.associateWith { memberId ->
            val participant = funnel.participantOf(memberId)
                ?: return@associateWith exclusionMissOf(input.source.membersById.getValue(memberId), input)
            funnel.missOf(participant, isGenerated = input.generatedAt != null)
        }
        return prePoolMissByMemberId + poolMissByMemberId
    }

    private fun loadPoolAtGeneration(input: OneToOneMissInput): List<MatchParticipant> {
        val lateCompleterIds = input.source.progresses
            .filter { PrePoolMiss.isCompletedAfter(it, input.generatedAt) }
            .map { it.memberId }
            .toSet()
        return matchmakingService.loadMatchingPoolParticipants(input.source.quizSet.id)
            .filter { it.memberId !in lateCompleterIds }
    }

    // 1:1 제외 정책(OneToOneExclusionPolicy)이 보는 두 조건을 실제 기록으로 확인한다.
    private fun exclusionMissOf(member: Member, input: OneToOneMissInput): MatchMiss = when {
        !member.isActive() -> MatchMiss(MatchMissReason.EXCLUDED_INACTIVE)
        member.id in input.acceptedMemberIds -> MatchMiss(MatchMissReason.EXCLUDED_ALREADY_MATCHED)
        else -> MatchMiss(MatchMissReason.EXCLUDED_OTHER)
    }

    private class OneToOneFunnel(pool: List<MatchParticipant>, processor: OneToOneMatchingProcessor) {
        private val poolById: Map<Long, MatchParticipant> = pool.associateBy { it.memberId }
        private val eligibleDuos: List<ScoredMatch> = processor.scoreEligibleDuos(pool)
        private val selectedDuos: List<ScoredMatch> = processor.selectTopRatio(eligibleDuos)
        private val bestEligibleScoreByMemberId: Map<Long, Double> = bestScoreByMemberId(eligibleDuos)
        private val selectedMemberIds: Set<Long> = selectedDuos.flatMap { it.memberIds }.toSet()
        private val certainMemberIds: Set<Long> = processor.memberIdsCertainToKeepCandidate(selectedDuos)
        private val cutoffScore: Double? = selectedDuos.minOfOrNull { it.score }

        fun participantOf(memberId: Long): MatchParticipant? = poolById[memberId]

        fun missOf(participant: MatchParticipant, isGenerated: Boolean): MatchMiss {
            val memberId = participant.memberId
            val bestScore = bestEligibleScoreByMemberId[memberId]
                ?: return MatchMiss(noEligiblePairReasonOf(participant))
            if (memberId !in selectedMemberIds) {
                return MatchMiss(MatchMissReason.CUT_BY_TOP_RATIO, bestScore = bestScore, cutoffScore = cutoffScore)
            }
            if (!isGenerated) return MatchMiss(MatchMissReason.NOT_GENERATED)
            if (memberId in certainMemberIds) return MatchMiss(MatchMissReason.STATE_CHANGED_AFTER_GENERATION)
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
                    duo.memberIds.forEach { memberId ->
                        merge(memberId, duo.score) { current, other -> maxOf(current, other) }
                    }
                }
            }
    }
}

/** [acceptedMemberIds]는 이 퀴즈셋에서 1:1이 성사된 회원이다. */
class OneToOneMissInput(
    val source: MatchingReadSource,
    val candidateOwnerIds: Set<Long>,
    val acceptedMemberIds: Set<Long>,
    val generatedAt: LocalDateTime?,
)
