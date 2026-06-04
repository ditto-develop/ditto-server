package com.ditto.api.match.matching

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class OneToOneMatchingProcessorTest : FreeSpec(
    {
        val processor = OneToOneMatchingProcessor()

        "match() 종단 동작" - {
            "점수화 → 상위20%+동점 → 5명 제한 전체 파이프라인이 동작한다" {
                val p1 = MatchParticipant(1L, mapOf(101L to 1L, 102L to 1L, 103L to 1L))
                val p2 = MatchParticipant(2L, mapOf(101L to 1L, 102L to 1L, 103L to 2L)) // 1 과 2개 일치
                val p3 = MatchParticipant(3L, mapOf(101L to 1L, 102L to 2L, 103L to 2L)) // 1 과 1개, 2 와 2개 일치

                // 점수(3문항): (1,2)=66.7, (1,3)=33.3, (2,3)=66.7 → 상위20%+동점으로 66.7 두 쌍 선발
                val result = processor.match(listOf(p1, p2, p3))

                result.survivingDuos.map { it.memberId1 to it.memberId2 } shouldContainExactlyInAnyOrder listOf(
                    1L to 2L,
                    2L to 3L,
                )
            }

            "참여자가 2명 미만이면 빈 결과 (7.1)" {
                processor.match(emptyList()).survivingDuos shouldBe emptyList()
                processor.match(
                    listOf(MatchParticipant(1L, mapOf(101L to 1L))),
                ).survivingDuos shouldBe emptyList()
            }

            "참여자가 정확히 2명이면 페어 1개가 결과로 나온다" {
                val p1 = MatchParticipant(1L, mapOf(101L to 1L, 102L to 1L))
                val p2 = MatchParticipant(2L, mapOf(101L to 1L, 102L to 2L)) // 2문항 중 1개 일치

                val result = processor.match(listOf(p1, p2))

                result.survivingDuos.map { it.memberId1 to it.memberId2 } shouldContainExactlyInAnyOrder listOf(1L to 2L)
            }
        }
    },
)
