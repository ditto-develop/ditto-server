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

        // member 에게 99점짜리 상대 5명을 붙인다. 그러면 member 는 더 낮은 점수의 상대를 고르지 않는다.
        fun betterDuosOf(member: Long) = (1L..5L).map { duo(member, member * 1000 + it, 99.0) }

        "select" - {
            "내가 고르지 않은 매칭도 상대가 고르면 남는다" {
                // x는 6번째 상대를 고르지 않지만, 6번째 상대는 x밖에 없어서 x를 고른다.
                val x = 100L
                val scoredDuos = (1L..6L).map { duo(x, it, (10 - it).toDouble()) }

                TopPicksSelector.select(scoredDuos.asSequence(), 5) shouldContainExactlyInAnyOrder scoredDuos
            }

            "양쪽 모두 고르지 않은 매칭만 빠진다" {
                val x = 100L
                val sixthPartner = 6L
                val xDuos = (1L..sixthPartner).map { duo(x, it, (10 - it).toDouble()) }

                val selected = TopPicksSelector.select((xDuos + betterDuosOf(sixthPartner)).asSequence(), 5)

                selected shouldContainExactlyInAnyOrder xDuos.take(5) + betterDuosOf(sixthPartner)
            }

            "모든 상대에게서 꼴찌인 회원도 자기가 고른 상위 5명을 받는다" {
                // 여자 6명은 모두 남자 5명과 80점이라 lowScoredMan 을 고르지 않는다.
                // lowScoredMan 은 자기와 점수가 높은 여자 2~6번을 고른다.
                val women = (1L..6L)
                val popularMen = (11L..15L)
                val lowScoredMan = 16L
                fun lowScoredDuo(woman: Long) = duo(woman, lowScoredMan, (10 + woman).toDouble())
                val scoredDuos = women.flatMap { woman ->
                    popularMen.map { man -> duo(woman, man, 80.0) } + lowScoredDuo(woman)
                }

                val selected = TopPicksSelector.select(scoredDuos.asSequence(), 5)

                selected.filter { lowScoredMan in it.memberIds } shouldContainExactlyInAnyOrder
                    (2L..6L).map { lowScoredDuo(it) }
            }

            "여러 사람이 고른 회원은 5명을 넘게 받는다" {
                // star가 고르는 건 5명이지만 7명 모두 star를 고른다.
                val star = 100L
                val scoredDuos = (1L..7L).map { duo(star, it, 90.0 - it) }

                val selected = TopPicksSelector.select(scoredDuos.asSequence(), 5)

                selected.count { star in it.memberIds } shouldBe 7
            }

            "회원마다 상대가 5명 이하면 모두 남는다" {
                val scoredDuos = listOf(
                    duo(1L, 2L, 8.0),
                    duo(1L, 3L, 7.0),
                    duo(2L, 3L, 7.0),
                    duo(2L, 4L, 7.0),
                )

                TopPicksSelector.select(scoredDuos.asSequence(), 5) shouldContainExactlyInAnyOrder scoredDuos
            }

            "동점은 무작위로 자르고 점수가 더 높은 상대는 항상 고른다" {
                // 상대들은 x를 고르지 않아서 남는 건 x가 고른 것뿐이다.
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
                val othersBetterDuos = (1L..7L).flatMap { betterDuosOf(it) }

                val selected = TopPicksSelector.select((scoredDuos + othersBetterDuos).asSequence(), 5, Random(42))
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

                val first = TopPicksSelector.select(scoredDuos.asSequence(), 2, Random(42))
                val second = TopPicksSelector.select(scoredDuos.asSequence(), 2, Random(42))

                first shouldBe second
            }

            "남은 매칭은 들어온 순서대로 돌려준다" {
                val x = 100L
                // 점수 순(2, 1)과 들어온 순(1, 2)이 다르게 둔다.
                val scoredDuos = listOf(
                    duo(x, 1L, 7.0),
                    duo(x, 2L, 9.0),
                    duo(x, 3L, 1.0),
                    duo(x, 4L, 5.0),
                )
                val othersBetterDuos = (1L..4L).flatMap { betterDuosOf(it) }

                val selected = TopPicksSelector.select(scoredDuos.asSequence() + othersBetterDuos, 2)

                selected.filter { x in it.memberIds } shouldBe listOf(duo(x, 1L, 7.0), duo(x, 2L, 9.0))
            }

            "동점이 없으면 회원마다 상위 5개를 모아 합친 것과 같다" {
                val random = Random(7)
                val memberIds = (1L..30L).toList()
                val pairs = memberIds.flatMapIndexed { index, memberA ->
                    memberIds.drop(index + 1).map { memberB -> memberA to memberB }
                }
                val distinctScores = pairs.indices.shuffled(random).map { it / 10.0 }
                val scoredDuos = pairs.zip(distinctScores) { (memberA, memberB), score -> duo(memberA, memberB, score) }
                val expectedPicks = memberIds.flatMapTo(mutableSetOf()) { memberId ->
                    scoredDuos.filter { memberId in it.memberIds }.sortedByDescending { it.score }.take(5)
                }

                val selected = TopPicksSelector.select(scoredDuos.asSequence(), 5, random)

                selected shouldBe scoredDuos.filter { it in expectedPicks }
            }

            "빈 입력이면 빈 결과다" {
                TopPicksSelector.select(emptySequence(), 5) shouldBe emptyList()
            }
        }
    },
)
