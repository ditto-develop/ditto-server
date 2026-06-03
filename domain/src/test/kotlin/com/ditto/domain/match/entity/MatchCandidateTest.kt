package com.ditto.domain.match.entity

import com.ditto.domain.match.MatchCandidateFixture
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.support.IntegrationTest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import javax.sql.DataSource

class MatchCandidateTest(
    private val matchCandidateRepository: MatchCandidateRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "MatchCandidate 저장/조회" - {
        "owner_member_id + quiz_set_id 로 후보 목록을 조회한다" {
            matchCandidateRepository.save(MatchCandidateFixture.create(ownerMemberId = 1L, otherMemberId = 2L, quizSetId = 10L, score = 80.0))
            matchCandidateRepository.save(MatchCandidateFixture.create(ownerMemberId = 1L, otherMemberId = 3L, quizSetId = 10L, score = 50.0))
            matchCandidateRepository.save(MatchCandidateFixture.create(ownerMemberId = 1L, otherMemberId = 4L, quizSetId = 99L, score = 70.0))

            matchCandidateRepository.findByOwnerMemberIdAndQuizSetId(1L, 10L) shouldHaveSize 2
        }

        "동일 (owner, other, quizSet) 조합 중복 저장은 UK 위반으로 예외" {
            matchCandidateRepository.save(MatchCandidateFixture.create(ownerMemberId = 1L, otherMemberId = 2L, quizSetId = 10L))

            shouldThrow<Exception> {
                matchCandidateRepository.saveAndFlush(
                    MatchCandidateFixture.create(ownerMemberId = 1L, otherMemberId = 2L, quizSetId = 10L, score = 50.0),
                )
            }
        }
    }

    "quizSet 단위 멱등/삭제" - {
        "existsByQuizSetId 로 계산 여부를 판단한다" {
            matchCandidateRepository.existsByQuizSetId(10L) shouldBe false

            matchCandidateRepository.save(MatchCandidateFixture.create(quizSetId = 10L))

            matchCandidateRepository.existsByQuizSetId(10L) shouldBe true
        }

        "deleteByQuizSetId 로 해당 셋 후보만 모두 삭제한다 (재계산용)" {
            matchCandidateRepository.save(MatchCandidateFixture.create(ownerMemberId = 1L, otherMemberId = 2L, quizSetId = 10L))
            matchCandidateRepository.save(MatchCandidateFixture.create(ownerMemberId = 2L, otherMemberId = 1L, quizSetId = 10L))
            matchCandidateRepository.save(MatchCandidateFixture.create(ownerMemberId = 1L, otherMemberId = 2L, quizSetId = 99L))

            matchCandidateRepository.deleteByQuizSetId(10L)

            matchCandidateRepository.findByOwnerMemberIdAndQuizSetId(1L, 10L) shouldHaveSize 0
            matchCandidateRepository.existsByQuizSetId(99L) shouldBe true
        }
    }
})
