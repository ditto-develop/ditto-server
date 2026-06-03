package com.ditto.api.match.matching

import kotlin.random.Random

/**
 * 1인 [hardLimit] 제한 (양방향).
 *
 * 회원별로 점수 desc 로 정렬해 상위 [hardLimit] 만 "유지 집합"으로 둔다.
 * 경계에서 점수가 같은 경우(동점)는 문서 정책에 따라 [random] 으로 무작위 처리한다.
 * (ID 같은 결정적 키로 깨면 특정 회원이 체계적으로 유리해지므로)
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
