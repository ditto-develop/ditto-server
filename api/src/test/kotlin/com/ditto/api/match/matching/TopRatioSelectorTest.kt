package com.ditto.api.match.matching

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class TopRatioSelectorTest : FreeSpec(
    {
        val a = 1L
        val b = 2L
        val c = 3L
        val d = 4L
        val e = 5L

        "select" - {
            // 문서 3.2 선발 예시: 5명/10페어, 상위 20% = 2개, 커트라인 7점 → 7점 모두 포함
            "커트라인 점수와 동일한 점수의 페어를 모두 포함한다" {
                val scoredDuos = listOf(
                    ScoredDuo.of(d, e, 8.0),
                    ScoredDuo.of(b, d, 7.0),
                    ScoredDuo.of(b, e, 7.0),
                    ScoredDuo.of(c, e, 7.0),
                    ScoredDuo.of(a, b, 6.0),
                    ScoredDuo.of(a, d, 5.0),
                    ScoredDuo.of(b, c, 4.0),
                    ScoredDuo.of(c, d, 4.0),
                    ScoredDuo.of(a, e, 3.0),
                    ScoredDuo.of(a, c, 2.0),
                )

                TopRatioSelector.select(scoredDuos, 0.2) shouldContainExactlyInAnyOrder listOf(
                    ScoredDuo.of(d, e, 8.0),
                    ScoredDuo.of(b, d, 7.0),
                    ScoredDuo.of(b, e, 7.0),
                    ScoredDuo.of(c, e, 7.0),
                )
            }

            "빈 입력이면 빈 결과를 반환한다" {
                TopRatioSelector.select(emptyList(), 0.2) shouldBe emptyList()
            }

            "모든 페어가 동점이면 전부 선발된다 (7.3)" {
                val scoredDuos = listOf(
                    ScoredDuo.of(a, b, 5.0),
                    ScoredDuo.of(a, c, 5.0),
                    ScoredDuo.of(b, c, 5.0),
                )

                TopRatioSelector.select(scoredDuos, 0.2) shouldHaveSize 3
            }

            "페어가 1개면 그대로 선발된다" {
                val scoredDuos = listOf(ScoredDuo.of(a, b, 50.0))

                TopRatioSelector.select(scoredDuos, 0.2) shouldContainExactlyInAnyOrder scoredDuos
            }

            "topRatio 가 1.0 이상이면 전부 선발된다" {
                val scoredDuos = listOf(
                    ScoredDuo.of(a, b, 80.0),
                    ScoredDuo.of(a, c, 50.0),
                    ScoredDuo.of(b, c, 20.0),
                )

                TopRatioSelector.select(scoredDuos, 1.0) shouldContainExactlyInAnyOrder scoredDuos
            }
        }
    },
)
