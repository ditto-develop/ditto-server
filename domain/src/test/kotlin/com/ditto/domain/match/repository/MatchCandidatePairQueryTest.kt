package com.ditto.domain.match.repository

import com.ditto.domain.match.MatchCandidateFixture
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.shouldBe
import javax.sql.DataSource

private const val QUIZ_SET = 1L
private const val OTHER_QUIZ_SET = 2L
private const val ONE = 1L
private const val OTHER = 2L

class MatchCandidatePairQueryTest(
    private val matchCandidateRepository: MatchCandidateRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "existsPairByQuizSetId — 서로의 후보로 노출됐는지 (성사 전 소개노트 열람 권한)" - {
        "given: 한 방향(owner→other) 행만 있을 때" - {
            "when: 그 방향으로 조회하면" - {
                "then: true 다" {
                    matchCandidateRepository.save(
                        MatchCandidateFixture.create(ownerMemberId = ONE, otherMemberId = OTHER, quizSetId = QUIZ_SET),
                    )

                    matchCandidateRepository.existsPairByQuizSetId(ONE, OTHER, QUIZ_SET) shouldBe true
                }
            }

            "when: 반대 방향으로 조회하면" - {
                "then: 방향 무관으로 판정해 true 다 — 한 방향 행이 유실돼도 양쪽 화면이 같이 동작해야 한다" {
                    matchCandidateRepository.save(
                        MatchCandidateFixture.create(ownerMemberId = ONE, otherMemberId = OTHER, quizSetId = QUIZ_SET),
                    )

                    matchCandidateRepository.existsPairByQuizSetId(OTHER, ONE, QUIZ_SET) shouldBe true
                }
            }
        }

        "given: 후보 행이 다른 퀴즈셋에만 있을 때" - {
            "when: 이번 주 퀴즈셋으로 조회하면" - {
                "then: false 다 — 지난 주 후보로 열람 권한이 남지 않는다" {
                    matchCandidateRepository.save(
                        MatchCandidateFixture.create(
                            ownerMemberId = ONE,
                            otherMemberId = OTHER,
                            quizSetId = OTHER_QUIZ_SET,
                        ),
                    )

                    matchCandidateRepository.existsPairByQuizSetId(ONE, OTHER, QUIZ_SET) shouldBe false
                }
            }
        }

        "같은 퀴즈셋에 다른 상대의 후보만 있으면 false 다" {
            matchCandidateRepository.save(
                MatchCandidateFixture.create(ownerMemberId = ONE, otherMemberId = 9L, quizSetId = QUIZ_SET),
            )

            matchCandidateRepository.existsPairByQuizSetId(ONE, OTHER, QUIZ_SET) shouldBe false
        }

        "후보가 아예 없으면 false 다" {
            matchCandidateRepository.existsPairByQuizSetId(ONE, OTHER, QUIZ_SET) shouldBe false
        }
    }
})
