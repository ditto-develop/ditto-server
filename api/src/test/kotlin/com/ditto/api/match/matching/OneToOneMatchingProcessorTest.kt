package com.ditto.api.match.matching

import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.GenderPreference
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlin.random.Random

class OneToOneMatchingProcessorTest : FreeSpec(
    {
        val processor = OneToOneMatchingProcessor()

        // 결과 검증용: 구성원 2명을 (작은 ID, 큰 ID) 순서로 편다.
        fun asPair(match: ScoredMatch): Pair<Long, Long> =
            match.memberIds.sorted().let { (smallerId, largerId) -> smallerId to largerId }

        // 점수 파이프라인 검증용: 성별·나이 필터가 개입하지 않도록 모두 같은 성별·나이 + ANY 선호.
        fun scored(id: Long, answers: Map<Long, Long>) =
            MatchParticipant(id, answers, gender = Gender.MALE, age = 25, preferredGender = GenderPreference.ANY)

        // 자격 필터 검증용: 두 문항 모두 일치(=100점)하는 답변에 성별/나이/선호만 달리한다.
        fun participant(id: Long, gender: Gender, preferredGender: GenderPreference, age: Int = 25) =
            MatchParticipant(id, mapOf(101L to 1L, 102L to 1L), gender = gender, age = age, preferredGender = preferredGender)

        "match() 종단 동작" - {
            "점수가 낮은 페어도 회원별 상위 5명에 들면 후보가 된다" {
                val p1 = scored(1L, mapOf(101L to 1L, 102L to 1L, 103L to 1L))
                val p2 = scored(2L, mapOf(101L to 1L, 102L to 1L, 103L to 2L)) // 1 과 2개 일치
                val p3 = scored(3L, mapOf(101L to 1L, 102L to 2L, 103L to 2L)) // 1 과 1개, 2 와 2개 일치

                // 점수(3문항): (1,2)=66.7, (1,3)=33.3, (2,3)=66.7
                val result = processor.match(listOf(p1, p2, p3))

                result.map { asPair(it) } shouldContainExactlyInAnyOrder listOf(
                    1L to 2L,
                    1L to 3L,
                    2L to 3L,
                )
            }

            "참여자가 2명 미만이면 빈 결과 (7.1)" {
                processor.match(emptyList()) shouldBe emptyList()
                processor.match(listOf(scored(1L, mapOf(101L to 1L)))) shouldBe emptyList()
            }

            "참여자가 정확히 2명이면 페어 1개가 결과로 나온다" {
                val p1 = scored(1L, mapOf(101L to 1L, 102L to 1L))
                val p2 = scored(2L, mapOf(101L to 1L, 102L to 2L)) // 2문항 중 1개 일치

                val result = processor.match(listOf(p1, p2))

                result.map { asPair(it) } shouldContainExactlyInAnyOrder listOf(1L to 2L)
            }
        }

        "단계별 공개 메서드" - {
            "findMemberIdsWithEligiblePair 는 자격 상대가 한 명이라도 있는 회원만 돌려준다" {
                val male = participant(1L, Gender.MALE, GenderPreference.OPPOSITE)
                val female = participant(2L, Gender.FEMALE, GenderPreference.OPPOSITE)
                val farFemale = participant(3L, Gender.FEMALE, GenderPreference.OPPOSITE, age = 40)

                processor.findMemberIdsWithEligiblePair(listOf(male, female, farFemale)) shouldBe setOf(1L, 2L)
            }

            "findMemberIdsWithEligiblePair 가 고른 회원은 match() 에서 빠짐없이 후보를 받는다" {
                val random = Random(11)
                val pool = (1L..40L).map { id ->
                    MatchParticipant(
                        memberId = id,
                        answers = (101L..105L).associateWith { random.nextLong(1, 3) },
                        gender = Gender.entries.random(random),
                        age = random.nextInt(20, 45),
                        preferredGender = GenderPreference.entries.random(random),
                        blockedMemberIds = setOf(random.nextLong(1, 41)),
                    )
                }

                val memberIdsWithCandidate = processor.match(pool).flatMapTo(mutableSetOf()) { it.memberIds }

                processor.findMemberIdsWithEligiblePair(pool) shouldBe memberIdsWithCandidate
            }

            "findMemberIdsWithEligiblePair 는 자기 자신을 상대로 세지 않는다" {
                val alone = participant(1L, Gender.MALE, GenderPreference.ANY)

                processor.findMemberIdsWithEligiblePair(listOf(alone)) shouldBe emptySet()
            }
        }

        "성별 상호호환 하드 필터" - {
            "서로의 성별 선호를 모두 충족하면 페어가 된다 (남↔여, 둘 다 이성 선호)" {
                val male = participant(1L, Gender.MALE, GenderPreference.OPPOSITE)
                val female = participant(2L, Gender.FEMALE, GenderPreference.OPPOSITE)

                processor.match(listOf(male, female))
                    .map { asPair(it) } shouldContainExactlyInAnyOrder listOf(1L to 2L)
            }

            "한쪽 선호라도 어긋나면 페어에서 제외된다 (남(이성선호) ↔ 여(동성선호))" {
                val male = participant(1L, Gender.MALE, GenderPreference.OPPOSITE) // 여성을 원함
                val femaleWantingFemale = participant(2L, Gender.FEMALE, GenderPreference.SAME) // 여성을 원함 → 남성 거부

                processor.match(listOf(male, femaleWantingFemale)).shouldBeEmpty()
            }

            "동성 선호끼리 같은 성별이면 페어가 된다 (남↔남, 둘 다 동성 선호)" {
                val m1 = participant(1L, Gender.MALE, GenderPreference.SAME)
                val m2 = participant(2L, Gender.MALE, GenderPreference.SAME)

                processor.match(listOf(m1, m2))
                    .map { asPair(it) } shouldContainExactlyInAnyOrder listOf(1L to 2L)
            }

            "ANY 선호는 상대 성별과 무관하게 호환된다" {
                val anyPref = participant(1L, Gender.MALE, GenderPreference.ANY)
                val female = participant(2L, Gender.FEMALE, GenderPreference.ANY)

                processor.match(listOf(anyPref, female))
                    .map { asPair(it) } shouldContainExactlyInAnyOrder listOf(1L to 2L)
            }
        }

        "나이차 하드 필터" - {
            "나이차가 10을 넘으면 페어에서 제외된다" {
                val young = participant(1L, Gender.MALE, GenderPreference.ANY, age = 20)
                val old = participant(2L, Gender.FEMALE, GenderPreference.ANY, age = 31) // 차이 11

                processor.match(listOf(young, old)).shouldBeEmpty()
            }

            "나이차가 정확히 10이면 매칭된다 (경계 포함)" {
                val a = participant(1L, Gender.MALE, GenderPreference.ANY, age = 20)
                val b = participant(2L, Gender.FEMALE, GenderPreference.ANY, age = 30) // 차이 10

                processor.match(listOf(a, b))
                    .map { asPair(it) } shouldContainExactlyInAnyOrder listOf(1L to 2L)
            }
        }
    },
)
