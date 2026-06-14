package com.ditto.application.match.matching

import kotlin.random.Random

/**
 * 1인 [hardLimit] 제한 (양방향).
 *
 * 회원별로 무작위로 섞은 뒤 점수 desc 로 정렬해 상위 [hardLimit] 만 "유지 집합"으로 둔다.
 * 정렬이 stable 하므로 점수가 다르면 결과는 결정적이고, **동점일 때만** 무작위 순서가 된다.
 * (ID 같은 결정적 키로 깨면 특정 회원이 체계적으로 유리해지므로 무작위로 처리)
 * 페어는 양쪽 모두의 유지 집합에 포함될 때만 생존한다.
 * (한쪽에서 제외되면 반대쪽 노출 목록에서도 제거되는 양방향 원칙)
 */
object HardLimitApplier {

    fun apply(
        scoredDuos: List<ScoredDuo>,
        hardLimit: Int,
        random: Random = Random.Default,
    ): List<ScoredDuo> {
        if (scoredDuos.isEmpty()) return scoredDuos

        val scoredDuoByMemberId = buildMap<Long, MutableList<ScoredDuo>> {
            scoredDuos.forEach { scoredDuo ->
                getOrPut(scoredDuo.memberId1) { mutableListOf() }.add(scoredDuo)
                getOrPut(scoredDuo.memberId2) { mutableListOf() }.add(scoredDuo)
            }
        }

        val selectedDuoByMemberId = scoredDuoByMemberId.mapValues { (_, memberDuos) ->
            // 미리 섞은 뒤 stable 정렬 → 점수는 결정적, 동점만 무작위.
            // (random 을 comparator 안에서 직접 호출하면 비교 일관성이 깨지므로 shuffle 로 처리)
            memberDuos
                .shuffled(random)
                .sortedByDescending { it.score }
                .take(hardLimit)
                .toSet()
        }

        return scoredDuos.filter { scoredDuo -> isSelectedByBothMembers(scoredDuo, selectedDuoByMemberId) }
    }

    /**
     * 양방향 생존 조건: 페어가 양쪽 회원 모두의 선발 집합에 포함되어야 살아남는다.
     * 한쪽 회원이 상위 N명 밖으로 밀어내 한쪽 집합에만 있으면 제거된다. (양방향 원칙)
     */
    private fun isSelectedByBothMembers(
        scoredDuo: ScoredDuo,
        selectedDuoByMemberId: Map<Long, Set<ScoredDuo>>,
    ): Boolean =
        selectedDuoByMemberId.getValue(scoredDuo.memberId1).contains(scoredDuo) &&
            selectedDuoByMemberId.getValue(scoredDuo.memberId2).contains(scoredDuo)
}
