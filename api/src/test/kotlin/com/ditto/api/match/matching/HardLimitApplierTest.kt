package com.ditto.api.match.matching

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlin.random.Random

class HardLimitApplierTest : FreeSpec(
    {
        val a = 1L
        val b = 2L
        val c = 3L
        val d = 4L
        val e = 5L
        val f = 6L
        val g = 7L

        "apply" - {
            // 문서 4. 예시: E 의 후보 6명 → 상위 5명 유지, G 제외 (E-G 양방향 삭제)
            "5명 초과 시 점수 상위 5명만 유지한다" {
                val scoredDuos = listOf(
                    ScoredDuo.of(e, a, 10.0),
                    ScoredDuo.of(e, b, 9.0),
                    ScoredDuo.of(e, c, 9.0),
                    ScoredDuo.of(e, d, 8.0),
                    ScoredDuo.of(e, f, 7.0),
                    ScoredDuo.of(e, g, 6.0),
                )

                HardLimitApplier.apply(scoredDuos, 5) shouldContainExactlyInAnyOrder listOf(
                    ScoredDuo.of(e, a, 10.0),
                    ScoredDuo.of(e, b, 9.0),
                    ScoredDuo.of(e, c, 9.0),
                    ScoredDuo.of(e, d, 8.0),
                    ScoredDuo.of(e, f, 7.0),
                )
            }

            "한쪽에서 제외된 페어는 반대쪽 목록에서도 사라진다 (양방향 원칙)" {
                // x 가 5명을 초과(6 페어). 6번째로 밀린 상대와의 페어는 상대 입장에선 유일하지만 제거된다.
                val x = 100L
                val scoredDuos = (1L..6L).map { ScoredDuo.of(x, it, (10 - it).toDouble()) }

                val survivors = HardLimitApplier.apply(scoredDuos, 5)

                survivors shouldHaveSize 5
                survivors.any { it == ScoredDuo.of(x, 6L, 4.0) } shouldBe false // 가장 낮은 점수 페어 제거
            }

            "5명 이하면 모두 유지된다" {
                val scoredDuos = listOf(
                    ScoredDuo.of(d, e, 8.0),
                    ScoredDuo.of(b, d, 7.0),
                    ScoredDuo.of(b, e, 7.0),
                    ScoredDuo.of(c, e, 7.0),
                )

                HardLimitApplier.apply(scoredDuos, 5) shouldContainExactlyInAnyOrder scoredDuos
            }

            "경계에서 동점이면 무작위로 채우되 정확히 hardLimit 만 유지한다" {
                val x = 100L
                val scoredDuos = listOf(
                    ScoredDuo.of(x, 1L, 10.0),
                    ScoredDuo.of(x, 2L, 9.0),
                    ScoredDuo.of(x, 3L, 8.0),
                    ScoredDuo.of(x, 4L, 7.0),
                    ScoredDuo.of(x, 5L, 5.0), // 동점
                    ScoredDuo.of(x, 6L, 5.0), // 동점
                    ScoredDuo.of(x, 7L, 5.0), // 동점 — 셋 중 하나만 생존
                )

                val survivors = HardLimitApplier.apply(scoredDuos, 5, Random(42))

                survivors shouldHaveSize 5
                // 명확한 상위 4개(10,9,8,7)는 시드와 무관하게 항상 유지된다
                survivors shouldContainAll listOf(
                    ScoredDuo.of(x, 1L, 10.0),
                    ScoredDuo.of(x, 2L, 9.0),
                    ScoredDuo.of(x, 3L, 8.0),
                    ScoredDuo.of(x, 4L, 7.0),
                )
            }

            "빈 입력이면 빈 결과를 반환한다" {
                HardLimitApplier.apply(emptyList(), 5) shouldBe emptyList()
            }

            "두 회원이 동시에 5명을 초과해도 양쪽 top5 에 든 상호 페어는 생존한다" {
                val x = 100L
                val y = 200L
                val scoredDuos = listOf(
                    ScoredDuo.of(x, y, 95.0), // x·y 양쪽 모두 최상위 → 생존
                    ScoredDuo.of(x, 1L, 90.0),
                    ScoredDuo.of(x, 2L, 80.0),
                    ScoredDuo.of(x, 3L, 70.0),
                    ScoredDuo.of(x, 4L, 60.0),
                    ScoredDuo.of(x, 5L, 50.0), // x 의 6번째 → 제거
                    ScoredDuo.of(y, 6L, 90.0),
                    ScoredDuo.of(y, 7L, 80.0),
                    ScoredDuo.of(y, 8L, 70.0),
                    ScoredDuo.of(y, 9L, 60.0),
                    ScoredDuo.of(y, 10L, 50.0), // y 의 6번째 → 제거
                )

                val survivors = HardLimitApplier.apply(scoredDuos, 5)

                survivors shouldContain ScoredDuo.of(x, y, 95.0)
                survivors shouldNotContain ScoredDuo.of(x, 5L, 50.0)
                survivors shouldNotContain ScoredDuo.of(y, 10L, 50.0)
                survivors shouldHaveSize 9
            }
        }
    },
)
