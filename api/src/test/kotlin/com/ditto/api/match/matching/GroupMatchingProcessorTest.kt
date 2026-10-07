package com.ditto.api.match.matching

import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.GenderPreference
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlin.random.Random

class GroupMatchingProcessorTest : FreeSpec(
    {
        val processor = GroupMatchingProcessor(Random(42))

        // 그룹은 성별·나이 필터가 없으므로 전원 같은 값으로 두고 답변·차단만 달리한다.
        fun participant(
            id: Long,
            answers: Map<Long, Long>,
            blockedMemberIds: Set<Long> = emptySet(),
        ) = MatchParticipant(
            memberId = id,
            answers = answers,
            gender = Gender.MALE,
            age = 25,
            preferredGender = GenderPreference.ANY,
            blockedMemberIds = blockedMemberIds,
        )

        // 답변이 id·문항 번호에서 파생돼 페어마다 일치 수가 갈리는 풀.
        fun pool(size: Int, questionCount: Int = 5) =
            (1..size).map { id ->
                participant(
                    id.toLong(),
                    (1..questionCount).associate { question ->
                        (100L + question) to ((id + question) % 3 + 1).toLong()
                    },
                )
            }

        "match() 종단 동작" - {
            "참여자가 최소 인원(3명) 미만이면 빈 결과다" {
                processor.match(emptyList()).shouldBeEmpty()
                processor.match(pool(1)).shouldBeEmpty()
                processor.match(pool(2)).shouldBeEmpty()
            }

            "정원 안의 인원이면 전원이 한 그룹이 된다" {
                processor.match(pool(3)).single().memberIds shouldBe setOf(1L, 2L, 3L)
                processor.match(pool(6)).single().memberIds shouldBe (1L..6L).toSet()
            }

            "그룹 점수는 구성원 모든 페어 점수의 평균이다" {
                // 2문항: (1,2)=1개 일치=50.0, (1,3)=1개 일치=50.0, (2,3)=0개 일치=0.0
                val p1 = participant(1L, mapOf(101L to 1L, 102L to 1L))
                val p2 = participant(2L, mapOf(101L to 1L, 102L to 2L))
                val p3 = participant(3L, mapOf(101L to 2L, 102L to 1L))

                val result = processor.match(listOf(p1, p2, p3))

                // (50.0 + 50.0 + 0.0) / 3 = 33.33... → 소수점 1자리
                result.single().score shouldBe 33.3
            }

            "그룹은 문항 수 근거 없이 평균 점수만 갖는다" {
                val result = processor.match(pool(3))

                result.single().matchedQuestionCount shouldBe null
                result.single().totalQuestionCount shouldBe null
            }
        }

        "겹치지 않는 분할" - {
            // 화면이 그룹 후보를 하나만 보여주고, 겹친 그룹은 한쪽 성사로 무너진다 — 한 사람은 한 그룹에만 든다.
            fun List<ScoredMatch>.memberCounts() = flatMap { it.memberIds }.groupingBy { it }.eachCount()

            "한 사람은 최대 한 그룹에만 들고, 차단이 없으면 전원이 그룹을 받는다" {
                listOf(7, 11, 30, 57).forEach { size ->
                    val result = processor.match(pool(size))

                    result.memberCounts().values.all { it == 1 } shouldBe true
                    result.memberCounts().keys shouldBe (1L..size.toLong()).toSet()
                }
            }

            "인원은 정원 6명 안에서 균등하게 나뉜다" {
                processor.match(pool(7)).map { it.memberIds.size }.sorted() shouldBe listOf(3, 4)
                processor.match(pool(11)).map { it.memberIds.size }.sorted() shouldBe listOf(5, 6)
                processor.match(pool(13)).map { it.memberIds.size }.sorted() shouldBe listOf(4, 4, 5)
            }

            "참여자가 많아도 정원이 줄지 않는다" {
                processor.match(pool(30)).map { it.memberIds.size } shouldBe List(5) { 6 }
                processor.match(pool(31)).map { it.memberIds.size }.sorted() shouldBe listOf(5, 5, 5, 5, 5, 6)
            }

            "답이 비슷한 사람끼리 묶인다" {
                // 10명 → 5명 × 2. 1~5는 모두 같은 답, 6~10은 모두 다른 같은 답이다.
                val sameA = mapOf(101L to 1L, 102L to 1L, 103L to 1L)
                val sameB = mapOf(101L to 2L, 102L to 2L, 103L to 2L)
                val participants = (1L..5L).map { participant(it, sameA) } + (6L..10L).map { participant(it, sameB) }

                val result = processor.match(participants.shuffled(Random(7)))

                result.map { it.memberIds }.toSet() shouldBe setOf((1L..5L).toSet(), (6L..10L).toSet())
                result.forEach { it.score shouldBe 100.0 }
            }
        }

        "차단" - {
            val answers = mapOf(101L to 1L, 102L to 1L)

            "차단 관계인 두 사람은 같은 그룹에 들어가지 않는다 — 한 방향 차단도 양쪽에 적용된다" {
                val participants = listOf(
                    participant(1L, answers, blockedMemberIds = setOf(2L)),
                    participant(2L, answers),
                ) + (3L..8L).map { participant(it, answers) }

                val result = processor.match(participants)

                result.none { it.memberIds.containsAll(setOf(1L, 2L)) } shouldBe true
            }

            "6명 중 차단이 있으면 한 그룹 대신 3명씩 둘로 나눠 전원이 그룹을 받는다" {
                val participants = listOf(
                    participant(1L, answers, blockedMemberIds = setOf(2L)),
                    participant(2L, answers),
                ) + (3L..6L).map { participant(it, answers) }

                val result = processor.match(participants)

                result.map { it.memberIds.size } shouldBe listOf(3, 3)
                result.flatMap { it.memberIds }.toSet() shouldBe (1L..6L).toSet()
                result.none { it.memberIds.containsAll(setOf(1L, 2L)) } shouldBe true
            }

            "4명 중 차단이 있으면 나눌 수 없어 차단된 둘 중 한 명만 빠진다" {
                // 4명으로는 3+3을 못 만든다. 겹치는 후보를 두 개 내면 한쪽 성사로 다른 쪽이 무너질 뿐이다.
                val participants = listOf(
                    participant(1L, answers, blockedMemberIds = setOf(2L)),
                    participant(2L, answers),
                    participant(3L, answers),
                    participant(4L, answers),
                )

                val group = processor.match(participants).single()

                group.memberIds.size shouldBe 3
                group.memberIds.containsAll(setOf(3L, 4L)) shouldBe true
                (setOf(1L, 2L) - group.memberIds).size shouldBe 1
            }

            "차단이 여럿이어도 나눌 수 있으면 전원이 그룹을 받는다" {
                // 9명 → 5+4. 1-2, 3-4, 5-6 이 서로 차단이다.
                val blocks = mapOf(1L to setOf(2L), 3L to setOf(4L), 5L to setOf(6L))
                val participants = (1L..9L).map { participant(it, answers, blockedMemberIds = blocks[it].orEmpty()) }

                val result = processor.match(participants)

                result.flatMap { it.memberIds }.toSet() shouldBe (1L..9L).toSet()
                blocks.forEach { (a, targets) ->
                    targets.forEach { b -> result.none { it.memberIds.containsAll(setOf(a, b)) } shouldBe true }
                }
            }

            "빠지는 쪽은 매번 같은 사람이 아니다" {
                // ID 순으로 정하면 차단으로 한 명이 빠질 때 늘 같은 쪽이 빠진다.
                val participants = listOf(
                    participant(1L, answers, blockedMemberIds = setOf(2L)),
                    participant(2L, answers),
                    participant(3L, answers),
                    participant(4L, answers),
                )

                val excluded = (1..40).map { seed ->
                    val group = GroupMatchingProcessor(Random(seed)).match(participants).single()
                    (setOf(1L, 2L) - group.memberIds).single()
                }.toSet()

                excluded shouldBe setOf(1L, 2L)
            }
        }
    },
)
