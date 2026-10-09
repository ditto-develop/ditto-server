package com.ditto.api.admin.quiz

import com.ditto.api.admin.quiz.dto.MatchMiss
import com.ditto.api.admin.quiz.dto.MatchMissReason
import com.ditto.api.match.matching.MatchParticipant
import com.ditto.api.match.matching.OneToOneMatchingProcessor
import com.ditto.api.match.service.MatchmakingService
import com.ditto.domain.member.entity.Member
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 1:1 후보가 없는 참여자가 어느 단계에서 빠졌는지 지금 DB 상태로 다시 계산한다. 단계 순서는 이 클래스 한 곳에서 정한다.
 * 풀은 배치와 같은 계산에서 매칭 뒤에 완주한 사람을 뺀다. 섞이면 매칭 때는 없던 짝이 생겨 원래 참여자의 이유가 바뀐다.
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
        private val memberIdsWithEligiblePair: Set<Long> =
            processor.scoreEligibleDuos(pool).flatMap { it.memberIds }.toSet()

        fun participantOf(memberId: Long): MatchParticipant? = poolById[memberId]

        // 자격 상대가 있으면 매칭이 반드시 후보를 준다. 그런데도 없으면 매칭 전이거나 매칭 뒤에 상태가 바뀐 것이다.
        fun missOf(participant: MatchParticipant, isGenerated: Boolean): MatchMiss {
            if (participant.memberId !in memberIdsWithEligiblePair) {
                return MatchMiss(noEligiblePairReasonOf(participant))
            }
            if (!isGenerated) return MatchMiss(MatchMissReason.NOT_GENERATED)
            return MatchMiss(MatchMissReason.STATE_CHANGED_AFTER_GENERATION)
        }

        private fun noEligiblePairReasonOf(participant: MatchParticipant): MatchMissReason =
            if (participant.gender == null || participant.age == null) {
                MatchMissReason.UNKNOWN_GENDER_OR_AGE
            } else {
                MatchMissReason.NO_ELIGIBLE_PAIR
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
