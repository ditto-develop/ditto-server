package com.ditto.api.match.matching

import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.GenderPreference
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe

class GroupMatchingProcessorTest : FreeSpec(
    {
        val processor = GroupMatchingProcessor()

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

            "씨앗이 달라도 같은 조합이면 한 그룹으로 합쳐진다" {
                // 3명이면 정원도 3명이라 어느 씨앗에서 출발해도 {1,2,3} 하나뿐이다.
                val result = processor.match(pool(3))

                result.size shouldBe 1
                result.single().memberIds shouldBe setOf(1L, 2L, 3L)
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

        "차단" - {
            "차단 관계인 두 사람은 같은 그룹에 들어가지 않는다" {
                val answers = mapOf(101L to 1L, 102L to 1L)
                val participants = listOf(
                    participant(1L, answers, blockedMemberIds = setOf(2L)),
                    participant(2L, answers, blockedMemberIds = setOf(1L)),
                    participant(3L, answers),
                    participant(4L, answers),
                )

                val result = processor.match(participants)

                result.shouldNotBeEmpty()
                result.none { it.memberIds.containsAll(setOf(1L, 2L)) } shouldBe true
            }

            "차단으로 정원을 못 채워도 최소 인원을 넘으면 그룹이 된다" {
                // 4명 풀의 정원은 4명인데 1-2가 차단이라 어느 그룹도 3명까지만 찬다.
                val answers = mapOf(101L to 1L, 102L to 1L)
                val participants = listOf(
                    participant(1L, answers, blockedMemberIds = setOf(2L)),
                    participant(2L, answers, blockedMemberIds = setOf(1L)),
                    participant(3L, answers),
                    participant(4L, answers),
                )

                val result = processor.match(participants)

                result.forEach { it.memberIds.size shouldBe 3 }
            }
        }

    },
)
