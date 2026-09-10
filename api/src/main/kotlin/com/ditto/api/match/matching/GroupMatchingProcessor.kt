package com.ditto.api.match.matching

import com.ditto.api.match.matching.GroupMatchingProcessor.Companion.HARD_LIMIT
import com.ditto.api.match.matching.GroupMatchingProcessor.Companion.TOP_RATIO
import com.ditto.domain.quiz.entity.MatchingType
import org.springframework.stereotype.Component
import kotlin.math.roundToInt

/**
 * 그룹 매칭 프로세스 (씨앗 기반 그룹 구성).
 *
 * 참여자 한 명씩을 씨앗으로 삼아 자기와 잘 맞는 순서로 멤버를 채워 그룹 후보를 만들고, 같은 조합은
 * 하나로 합친 뒤 그룹 점수 상위 [TOP_RATIO] + 동점([TopRatioSelector])을 뽑아 1인 [HARD_LIMIT]개로
 * 제한한다([HardLimitApplier]). 선발 두 단계는 1:1과 같은 컴포넌트를 그대로 쓴다.
 *
 * 1:1과 달리 **성별·나이 하드 필터가 없다.** 여럿이 대화하는 자리라 기획에 그런 조건이 없고,
 * 애초에 3명 이상이 서로 전부 이성인 조합은 성별이 둘뿐이라 존재할 수 없다.
 * 차단만 반영해 차단 관계인 두 사람이 같은 그룹에 들어가지 않게 한다.
 */
@Component
class GroupMatchingProcessor : MatchingProcessor {

    override val matchingType: MatchingType = MatchingType.GROUP

    override fun match(participants: List<MatchParticipant>): List<ScoredMatch> {
        if (participants.size < GroupSizePolicy.MIN_SIZE) return emptyList()

        val scoreByMemberPair = scoreAllPairs(participants)
        val seedGroups =
            composeSeedGroups(participants, scoreByMemberPair, GroupSizePolicy.decide(participants.size))

        val selected = TopRatioSelector.select(seedGroups, TOP_RATIO)
        return HardLimitApplier.apply(selected, HARD_LIMIT)
    }

    /** 참여자 전원의 페어 점수를 양방향으로 미리 계산한다. 씨앗 정렬과 그룹 점수 계산이 반복 조회한다. */
    private fun scoreAllPairs(participants: List<MatchParticipant>): Map<Long, Map<Long, Double>> {
        val scoreByMemberPair = participants.associate { it.memberId to mutableMapOf<Long, Double>() }

        participants.forEachIndexed { index, participant ->
            participants.drop(index + 1).forEach { otherParticipant ->
                val score = MatchScoreCalculator.calculate(participant, otherParticipant).score
                scoreByMemberPair.getValue(participant.memberId)[otherParticipant.memberId] = score
                scoreByMemberPair.getValue(otherParticipant.memberId)[participant.memberId] = score
            }
        }
        return scoreByMemberPair
    }

    /**
     * 참여자 한 명씩을 씨앗으로 그룹을 만든다. 씨앗이 달라도 같은 조합이 나오면 하나로 합친다
     * — 점수는 멤버 구성에서만 나오므로 어느 쪽을 남겨도 같다.
     */
    private fun composeSeedGroups(
        participants: List<MatchParticipant>,
        scoreByMemberPair: Map<Long, Map<Long, Double>>,
        groupSize: Int,
    ): List<ScoredMatch> =
        participants
            .mapNotNull { seed -> composeSeedGroup(seed, participants, scoreByMemberPair, groupSize) }
            .distinctBy { it.memberIds }

    /**
     * 씨앗과 점수가 높은 순으로 멤버를 채운다. 이미 담긴 누군가와 차단 관계인 사람은 건너뛴다.
     * 차단 때문에 [groupSize]를 못 채워도 [GroupSizePolicy.MIN_SIZE]만 넘으면 그대로 쓰고, 못 넘으면 버린다.
     */
    private fun composeSeedGroup(
        seed: MatchParticipant,
        participants: List<MatchParticipant>,
        scoreByMemberPair: Map<Long, Map<Long, Double>>,
        groupSize: Int,
    ): ScoredMatch? {
        val scoreFromSeed = scoreByMemberPair.getValue(seed.memberId)
        val members = mutableListOf(seed)

        val candidatesSortedByScore = participants
            .filter { it.memberId != seed.memberId }
            .sortedByDescending { scoreFromSeed.getValue(it.memberId) }

        for (candidate in candidatesSortedByScore) {
            if (members.size == groupSize) break
            if (members.any { it.isBlockedWith(candidate) }) continue
            members.add(candidate)
        }

        if (members.size < GroupSizePolicy.MIN_SIZE) return null

        val memberIds = members.map { it.memberId }
        return ScoredMatch.group(memberIds.toSet(), averagePairScore(memberIds, scoreByMemberPair))
    }

    /** 그룹 점수 = 구성원 모든 페어 점수의 평균 (소수점 1자리, 페어 점수와 같은 자릿수). */
    private fun averagePairScore(
        memberIds: List<Long>,
        scoreByMemberPair: Map<Long, Map<Long, Double>>,
    ): Double {
        val pairScores = memberIds.flatMapIndexed { index, memberId ->
            memberIds.drop(index + 1).map { otherMemberId ->
                scoreByMemberPair.getValue(memberId).getValue(otherMemberId)
            }
        }
        return (pairScores.average() * 10).roundToInt() / 10.0
    }

    companion object {
        private const val TOP_RATIO = 0.2 // 그룹 점수 상위 20% 선발
        private const val HARD_LIMIT = 3 // 1인 최대 노출 3개 그룹
    }
}
