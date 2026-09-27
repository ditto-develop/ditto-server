package com.ditto.api.match.matching

import com.ditto.domain.quiz.entity.MatchingType
import org.springframework.stereotype.Component
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * 그룹 매칭 프로세스 (겹치지 않는 분할).
 *
 * 참여자를 **서로 겹치지 않는 그룹**으로 나눈다 — 한 사람은 최대 한 그룹에만 들어간다(#227).
 * 화면이 그룹 후보를 하나만 보여주고, 한 그룹이 성사되면 겹친 다른 그룹은 자동 거절로 무너져
 * 거기만 속한 사람이 매칭을 못 받기 때문이다. 그래서 1:1의 상위 비율 선발·1인 노출 제한을 쓰지 않는다.
 *
 * 우선순위는 **① 매칭 못 받는 사람 최소화 → ② 그룹 점수**다.
 * 1. 그룹 수를 정원([GroupSizePolicy])으로 시작해 `⌈N / 정원⌉`개로 잡고 인원을 균등하게 나눈다(7명 → 4+3).
 * 2. 차단 때문에 전원을 담지 못하면 그룹 수를 늘려 더 작게 나눈다(6명 + 차단 1건 → 3+3). 최소 [GroupSizePolicy.MIN_SIZE]명.
 * 3. 그래도 못 담은 사람만 빠진다(4명 + 차단 1건 → 3명 그룹 하나 — 4명으로는 3+3을 못 만든다).
 * 4. 배정이 끝나면 그룹 간 1:1 교환으로 점수를 올린다([improveBySwaps]).
 *
 * 1:1과 달리 **성별·나이 하드 필터가 없다.** 여럿이 대화하는 자리라 기획에 그런 조건이 없고,
 * 애초에 3명 이상이 서로 전부 이성인 조합은 성별이 둘뿐이라 존재할 수 없다.
 * 차단만 반영해 차단 관계인 두 사람이 같은 그룹에 들어가지 않게 한다.
 *
 * [random]은 같은 조건의 회원 순서를 섞는 데만 쓴다 — ID 순으로 정하면 차단으로 한 명이 빠질 때
 * 늘 같은 쪽이 빠진다(`HardLimitApplier`의 동점 처리와 같은 이유).
 */
@Component
class GroupMatchingProcessor(private val random: Random = Random.Default) : MatchingProcessor {

    override val matchingType: MatchingType = MatchingType.GROUP

    override fun match(participants: List<MatchParticipant>): List<ScoredMatch> {
        if (participants.size < GroupSizePolicy.MIN_SIZE) return emptyList()

        val scores = PairScores.of(participants)
        val partition = bestPartition(participants, scores)
        improveBySwaps(partition.groups, scores)

        return partition.groups.map { group ->
            val memberIds = group.map { it.memberId }
            ScoredMatch.group(memberIds.toSet(), scores.averageOf(memberIds))
        }
    }

    /**
     * 가장 적은 그룹 수부터 늘려 가며, 전원을 담는 첫 그룹 수에서 멈춘다 — 전원을 담을 수 있다면 그룹이
     * 클수록(수가 적을수록) 낫다. 그룹 수마다 회원 순서를 바꿔 [ATTEMPTS]번 시도하고 가장 나은 배정을 고른다.
     * 어느 그룹 수로도 전원을 못 담으면 가장 많이 담은 배정을 쓴다.
     */
    private fun bestPartition(participants: List<MatchParticipant>, scores: PairScores): Partition {
        val poolSize = participants.size
        val fewestGroups = ceilDiv(poolSize, GroupSizePolicy.decide(poolSize))
        val mostGroups = poolSize / GroupSizePolicy.MIN_SIZE

        var best: Partition? = null
        for (groupCount in fewestGroups..mostGroups) {
            val capacities = balancedCapacities(poolSize, groupCount)
            val bestAtCount = (1..ATTEMPTS)
                .map { assign(orderForAssignment(participants), capacities, scores) }
                .maxWith(Partition.BETTER)
            best = listOfNotNull(best, bestAtCount).maxWith(Partition.BETTER)
            if (bestAtCount.unassignedCount == 0) break
        }
        return requireNotNull(best)
    }

    /**
     * 차단이 많은 사람부터 배정한다 — 넣을 곳이 적은 사람을 먼저 넣어야 뒤에서 막히지 않는다(그래프 색칠의 DSatur 와 같은 착안).
     * 같은 수끼리는 무작위 순서다(미리 섞은 뒤 stable 정렬).
     */
    private fun orderForAssignment(participants: List<MatchParticipant>): List<MatchParticipant> =
        participants
            .shuffled(random)
            // 차단 수는 방향과 무관하게 센다 — 차단당한 쪽도 넣을 곳이 똑같이 줄어든다.
            .sortedByDescending { participant -> participants.count(participant::isBlockedWith) }

    /**
     * [order] 순서로 한 명씩, 넣을 수 있는(정원이 남고 차단이 없는) 그룹 중 기존 구성원과 점수 평균이 가장
     * 높은 곳에 넣는다. 빈 그룹은 다른 곳이 모두 막혔을 때만 연다 — 먼저 열면 첫 몇 명이 흩어져 점수가 떨어진다.
     * 넣을 곳이 없던 사람은 [placeLeftover]로 한 번 더 시도한다.
     */
    private fun assign(order: List<MatchParticipant>, capacities: List<Int>, scores: PairScores): Partition {
        val groups = capacities.map { mutableListOf<MatchParticipant>() }
        val leftovers = mutableListOf<MatchParticipant>()

        order.forEach { participant ->
            val target = groups.indices
                .filter { groups[it].size < capacities[it] && groups[it].none(participant::isBlockedWith) }
                .maxByOrNull { scores.affinity(participant, groups[it]) }
            if (target == null) leftovers += participant else groups[target] += participant
        }

        // 최소 인원에 못 미친 그룹은 성사될 수 없으므로 풀어서 다른 그룹에 넣어 본다.
        val tooSmall = groups.filter { it.size in 1 until GroupSizePolicy.MIN_SIZE }
        val formed = groups.filter { it.size >= GroupSizePolicy.MIN_SIZE }
        val retry = leftovers + tooSmall.flatten()
        tooSmall.forEach { it.clear() }

        val unassigned = retry.filterNot { placeLeftover(it, formed) }
        return Partition(groups = formed, unassignedCount = unassigned.size, scores = scores)
    }

    /**
     * 정원이 찬 곳에도 [GroupSizePolicy.MAX_SIZE]까지는 더 넣고, 차단 상대가 딱 한 명인 그룹이면 그 사람을
     * 다른 그룹으로 옮겨 자리를 만든다. 매칭 못 받는 사람을 줄이는 것이 그룹 크기 균형보다 우선이다.
     */
    private fun placeLeftover(participant: MatchParticipant, groups: List<MutableList<MatchParticipant>>): Boolean {
        groups.firstOrNull { it.size < GroupSizePolicy.MAX_SIZE && it.none(participant::isBlockedWith) }
            ?.let { it += participant; return true }

        for (group in groups) {
            val blocking = group.filter(participant::isBlockedWith).singleOrNull() ?: continue
            val destination = groups.firstOrNull { other ->
                other !== group && other.size < GroupSizePolicy.MAX_SIZE && other.none(blocking::isBlockedWith)
            } ?: continue
            group -= blocking
            destination += blocking
            group += participant
            return true
        }
        return false
    }

    /**
     * 서로 다른 그룹의 두 사람을 맞바꿔 그룹 안 페어 점수 합이 오르면 바꾼다. 더 오를 게 없거나 [MAX_SWAP_PASSES]번
     * 돌면 멈춘다. 인원은 바뀌지 않고 차단을 새로 만드는 교환은 하지 않는다.
     */
    private fun improveBySwaps(groups: List<MutableList<MatchParticipant>>, scores: PairScores) {
        repeat(MAX_SWAP_PASSES) {
            var improved = false
            for (i in groups.indices) {
                for (j in i + 1 until groups.size) {
                    improved = swapBetween(groups[i], groups[j], scores) || improved
                }
            }
            if (!improved) return
        }
    }

    private fun swapBetween(
        left: MutableList<MatchParticipant>,
        right: MutableList<MatchParticipant>,
        scores: PairScores,
    ): Boolean {
        var swapped = false
        for (x in left.indices) {
            for (y in right.indices) {
                val a = left[x]
                val b = right[y]
                val leftRest = left.filterIndexed { index, _ -> index != x }
                val rightRest = right.filterIndexed { index, _ -> index != y }
                if (leftRest.any(b::isBlockedWith) || rightRest.any(a::isBlockedWith)) continue

                val gain = scores.affinitySum(b, leftRest) - scores.affinitySum(a, leftRest) +
                    scores.affinitySum(a, rightRest) - scores.affinitySum(b, rightRest)
                if (gain > SWAP_EPSILON) {
                    left[x] = b
                    right[y] = a
                    swapped = true
                }
            }
        }
        return swapped
    }

    /** [poolSize]명을 [groupCount]개로 최대한 균등하게 나눈 정원. 큰 그룹이 앞에 온다. */
    private fun balancedCapacities(poolSize: Int, groupCount: Int): List<Int> {
        val base = poolSize / groupCount
        val larger = poolSize % groupCount
        return List(groupCount) { index -> if (index < larger) base + 1 else base }
    }

    private fun ceilDiv(a: Int, b: Int): Int = (a + b - 1) / b

    /** 한 번의 배정 결과. [groups]는 모두 [GroupSizePolicy.MIN_SIZE]명 이상이다. */
    private class Partition(
        val groups: List<MutableList<MatchParticipant>>,
        val unassignedCount: Int,
        scores: PairScores,
    ) {
        val score: Double = groups.sumOf { group -> scores.averageOf(group.map { it.memberId }) }

        companion object {
            /** 매칭 못 받는 사람이 적은 쪽, 같으면 그룹 점수 합이 높은 쪽. */
            val BETTER: Comparator<Partition> =
                compareByDescending<Partition> { it.unassignedCount }.thenBy { it.score }
        }
    }

    /** 참여자 전원의 페어 점수를 양방향으로 미리 계산해 둔다. 배정·교환이 반복 조회한다. */
    private class PairScores(private val scoreByMemberPair: Map<Long, Map<Long, Double>>) {

        fun of(a: Long, b: Long): Double = scoreByMemberPair.getValue(a).getValue(b)

        fun affinitySum(participant: MatchParticipant, group: List<MatchParticipant>): Double =
            group.sumOf { of(participant.memberId, it.memberId) }

        /** 기존 구성원과의 점수 평균. 빈 그룹은 다른 곳이 모두 막혔을 때만 열리도록 가장 낮게 둔다. */
        fun affinity(participant: MatchParticipant, group: List<MatchParticipant>): Double =
            if (group.isEmpty()) EMPTY_GROUP_AFFINITY else affinitySum(participant, group) / group.size

        /** 그룹 점수 = 구성원 모든 페어 점수의 평균 (소수점 1자리, 페어 점수와 같은 자릿수). */
        fun averageOf(memberIds: List<Long>): Double {
            val pairScores = memberIds.flatMapIndexed { index, memberId ->
                memberIds.drop(index + 1).map { otherMemberId -> of(memberId, otherMemberId) }
            }
            return (pairScores.average() * 10).roundToInt() / 10.0
        }

        companion object {
            private const val EMPTY_GROUP_AFFINITY = -1.0

            fun of(participants: List<MatchParticipant>): PairScores {
                val scoreByMemberPair = participants.associate { it.memberId to mutableMapOf<Long, Double>() }
                participants.forEachIndexed { index, participant ->
                    participants.drop(index + 1).forEach { other ->
                        val score = MatchScoreCalculator.calculate(participant, other).score
                        scoreByMemberPair.getValue(participant.memberId)[other.memberId] = score
                        scoreByMemberPair.getValue(other.memberId)[participant.memberId] = score
                    }
                }
                return PairScores(scoreByMemberPair)
            }
        }
    }

    companion object {
        private const val ATTEMPTS = 20 // 그룹 수마다 회원 순서를 바꿔 시도하는 횟수
        private const val MAX_SWAP_PASSES = 50
        private const val SWAP_EPSILON = 1e-9
    }
}
