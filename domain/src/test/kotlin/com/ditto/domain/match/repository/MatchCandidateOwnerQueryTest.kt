package com.ditto.domain.match.repository

import com.ditto.domain.match.MatchCandidateFixture
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.shouldBe
import javax.sql.DataSource

private const val QUIZ_SET = 1L
private const val OTHER_QUIZ_SET = 2L

class MatchCandidateOwnerQueryTest(
    private val matchCandidateRepository: MatchCandidateRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "findOwnerMemberIdsByQuizSetId — 후보를 받은 회원(알림 대상)" - {
        "given: 한 회원이 후보를 여러 명 받았을 때" - {
            "when: 조회하면" - {
                "then: 회원이 한 번만 나온다" {
                    matchCandidateRepository.save(
                        MatchCandidateFixture.create(ownerMemberId = 1L, otherMemberId = 2L, quizSetId = QUIZ_SET),
                    )
                    matchCandidateRepository.save(
                        MatchCandidateFixture.create(ownerMemberId = 1L, otherMemberId = 3L, quizSetId = QUIZ_SET),
                    )

                    matchCandidateRepository.findOwnerMemberIdsByQuizSetId(QUIZ_SET) shouldBe listOf(1L)
                }
            }
        }

        "given: 다른 퀴즈셋의 후보가 섞여 있을 때" - {
            "when: 특정 퀴즈셋으로 조회하면" - {
                "then: 그 주에 후보를 받은 회원만 나온다" {
                    matchCandidateRepository.save(
                        MatchCandidateFixture.create(ownerMemberId = 1L, quizSetId = QUIZ_SET),
                    )
                    matchCandidateRepository.save(
                        MatchCandidateFixture.create(ownerMemberId = 9L, quizSetId = OTHER_QUIZ_SET),
                    )

                    matchCandidateRepository.findOwnerMemberIdsByQuizSetId(QUIZ_SET) shouldBe listOf(1L)
                }
            }
        }

        "후보가 없으면 빈 목록이다 — 인원이 모자라 후보가 안 만들어진 주에는 알리지 않는다" {
            matchCandidateRepository.findOwnerMemberIdsByQuizSetId(QUIZ_SET).size shouldBe 0
        }
    }
})
