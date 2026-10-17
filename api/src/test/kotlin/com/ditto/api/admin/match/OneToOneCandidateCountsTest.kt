package com.ditto.api.admin.match

import com.ditto.api.match.matching.MatchScore
import com.ditto.api.match.matching.ScoredMatch
import com.ditto.api.match.service.CandidateGenerationSummary
import com.ditto.api.match.service.CandidateRowCounts
import com.ditto.domain.quiz.entity.MatchingType
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class OneToOneCandidateCountsTest : FreeSpec(
    {
        fun duo(memberA: Long, memberB: Long) =
            ScoredMatch.duo(memberA, memberB, MatchScore(score = 50.0, matchedQuestionCount = 1, totalQuestionCount = 2))

        fun summary(matchingType: MatchingType, participantCount: Int, matches: List<ScoredMatch>) =
            CandidateGenerationSummary(
                quizSetId = 1L,
                matchingType = matchingType,
                participantCount = participantCount,
                rowCounts = CandidateRowCounts(deletedCount = 0, savedCount = matches.size * 2),
                matches = matches,
            )

        "후보 0명인 회원 수와 후보를 받은 회원의 최소·최대 후보 수를 센다" {
            // 1은 2·3·4와, 5는 아무와도 이어지지 않았다.
            val matches = listOf(duo(1L, 2L), duo(1L, 3L), duo(1L, 4L))

            val counts = OneToOneCandidateCounts.of(summary(MatchingType.ONE_TO_ONE, 5, matches)).shouldNotBeNull()

            counts.memberCountWithoutCandidate shouldBe 1
            counts.minCandidateCount shouldBe 1
            counts.maxCandidateCount shouldBe 3
        }

        "그룹 퀴즈셋이면 세지 않는다" {
            OneToOneCandidateCounts.of(summary(MatchingType.GROUP, 3, emptyList())).shouldBeNull()
        }
    },
)
