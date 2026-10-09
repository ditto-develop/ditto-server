package com.ditto.api.match.matching

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlin.random.Random

class TopPicksSelectorTest : FreeSpec(
    {
        // 일치 문항 수는 선발과 상관없어서 0으로 둔다.
        fun duo(memberA: Long, memberB: Long, score: Double) =
            ScoredMatch.duo(
                memberAId = memberA,
                memberBId = memberB,
                matchScore = MatchScore(score = score, matchedQuestionCount = 0, totalQuestionCount = 0),
            )

        fun countByMemberId(matches: List<ScoredMatch>): Map<Long, Int> =
            matches.flatMap { it.memberIds }.groupingBy { it }.eachCount()

        "select" - {
            "내가 고르지 않은 매칭도 상대가 고르면 남는다" {
                // x는 6번째 상대를 고르지 않지만, 6번째 상대는 x밖에 없어서 x를 고른다.
                val x = 100L
                val scoredDuos = (1L..6L).map { duo(x, it, (10 - it).toDouble()) }

                TopPicksSelector.select(scoredDuos, 5) shouldContainExactlyInAnyOrder scoredDuos
            }

            "양쪽 모두 고르지 않은 매칭만 빠진다" {
                val x = 100L
                val xDuos = (1L..6L).map { duo(x, it, (10 - it).toDouble()) }
                // 6번째 상대는 x보다 잘 맞는 상대가 5명 있어서 x를 고르지 않는다.
                val betterDuosOfSixth = (1L..5L).map { duo(6L, 600 + it, 99.0) }

                val selected = TopPicksSelector.select(xDuos + betterDuosOfSixth, 5)

                selected shouldContainExactlyInAnyOrder xDuos.take(5) + betterDuosOfSixth
            }

            "모든 상대에게서 꼴찌인 회원도 자기가 고른 상위 5명을 받는다" {
                // 여자 6명 모두 남자 5명과 80점이고 lowScoredMan 과는 점수가 낮아서 아무도 lowScoredMan 을 고르지 않는다.
                val women = (1L..6L)
                val popularMen = (11L..15L)
                val lowScoredMan = 16L
                val scoredDuos = women.flatMap { woman ->
                    popularMen.map { man -> duo(woman, man, 80.0) } + duo(woman, lowScoredMan, (10 + woman).toDouble())
                }

                val selected = TopPicksSelector.select(scoredDuos, 5)

                selected.filter { lowScoredMan in it.memberIds } shouldContainExactlyInAnyOrder
                    (2L..6L).map { woman -> duo(woman, lowScoredMan, (10 + woman).toDouble()) }
            }

            "여러 사람이 고른 회원은 5명을 넘게 받는다" {
                // star가 고르는 건 5명이지만 7명 모두 star를 고른다.
                val star = 100L
                val scoredDuos = (1L..7L).map { duo(star, it, 90.0 - it) }

                val selected = TopPicksSelector.select(scoredDuos, 5)

                countByMemberId(selected)[star] shouldBe 7
            }

            "5명 이하면 모두 유지된다" {
                val scoredDuos = listOf(
                    duo(4L, 5L, 8.0),
                    duo(2L, 4L, 7.0),
                    duo(2L, 5L, 7.0),
                    duo(3L, 5L, 7.0),
                )

                TopPicksSelector.select(scoredDuos, 5) shouldContainExactlyInAnyOrder scoredDuos
            }

            "동점은 무작위로 자르고 점수가 더 높은 상대는 항상 고른다" {
                // 상대들은 x보다 잘 맞는 상대가 5명씩 있어서 x를 고르지 않는다. 남는 건 x가 고른 것뿐이다.
                val x = 100L
                val scoredDuos = listOf(
                    duo(x, 1L, 10.0),
                    duo(x, 2L, 9.0),
                    duo(x, 3L, 8.0),
                    duo(x, 4L, 7.0),
                    duo(x, 5L, 5.0),
                    duo(x, 6L, 5.0),
                    duo(x, 7L, 5.0),
                )
                val crowdedOthers = scoredDuos.flatMap { match ->
                    val other = match.memberIds.first { it != x }
                    (1L..5L).map { duo(other, other * 1000 + it, 99.0) }
                }

                val selected = TopPicksSelector.select(scoredDuos + crowdedOthers, 5, Random(42))
                val pickedByX = selected.filter { x in it.memberIds }

                pickedByX shouldHaveSize 5
                pickedByX shouldContainAll listOf(
                    duo(x, 1L, 10.0),
                    duo(x, 2L, 9.0),
                    duo(x, 3L, 8.0),
                    duo(x, 4L, 7.0),
                )
            }

            "같은 시드면 결과가 같다" {
                val x = 100L
                val scoredDuos = listOf(
                    duo(x, 1L, 10.0),
                    duo(x, 2L, 5.0),
                    duo(x, 3L, 5.0),
                    duo(x, 4L, 5.0),
                )

                val first = TopPicksSelector.select(scoredDuos, 2, Random(42))
                val second = TopPicksSelector.select(scoredDuos, 2, Random(42))

                first shouldBe second
            }

            "빈 입력이면 빈 결과다" {
                TopPicksSelector.select(emptyList(), 5) shouldBe emptyList()
            }
        }
    },
)
