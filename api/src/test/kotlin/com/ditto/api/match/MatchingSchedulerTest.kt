package com.ditto.api.match

import com.ditto.api.match.scheduler.MatchingScheduler
import com.ditto.api.support.IntegrationTest
import com.ditto.domain.match.MatchCandidateFixture
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.QuizAnswerFixture
import com.ditto.domain.quiz.QuizFixture
import com.ditto.domain.quiz.QuizProgressFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.repository.QuizAnswerRepository
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

class MatchingSchedulerTest(
    private val matchingScheduler: MatchingScheduler,
    private val memberRepository: MemberRepository,
    private val quizSetRepository: QuizSetRepository,
    private val quizRepository: QuizRepository,
    private val quizAnswerRepository: QuizAnswerRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val matchCandidateRepository: MatchCandidateRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    fun saveMember(nickname: String, gender: Gender, age: Int = 25): Long =
        memberRepository.save(
            MemberFixture.create(nickname = nickname, status = MemberStatus.ACTIVE, gender = gender, age = age),
        ).id

    fun saveOneToOneQuizSet(endDate: LocalDateTime): Long =
        quizSetRepository.save(QuizSetFixture.create(matchingType = MatchingType.ONE_TO_ONE, endDate = endDate)).id

    /** 두 문항으로 100점 매칭되는 회원 2명을 구성하고 완료 처리한다. 회원 ID 쌍을 반환. */
    fun setupMatchingPair(quizSetId: Long): Pair<Long, Long> {
        val quizId1 = quizRepository.save(QuizFixture.create(quizSetId = quizSetId, displayOrder = 1)).id
        val quizId2 = quizRepository.save(QuizFixture.create(quizSetId = quizSetId, displayOrder = 2)).id
        // 기본 선호(OPPOSITE)로 매칭되도록 이성 한 쌍으로 구성한다.
        val a = saveMember("회원A", Gender.MALE)
        val b = saveMember("회원B", Gender.FEMALE)
        listOf(a, b).forEach { memberId ->
            quizAnswerRepository.save(QuizAnswerFixture.create(memberId = memberId, quizId = quizId1, choiceId = 1L))
            quizAnswerRepository.save(QuizAnswerFixture.create(memberId = memberId, quizId = quizId2, choiceId = 1L))
            val progress = QuizProgressFixture.create(memberId = memberId, quizSetId = quizSetId, totalCount = 2)
            repeat(2) { progress.recordAnswer() }
            quizProgressRepository.save(progress)
        }
        return a to b
    }

    "generateForEndedQuizSets" - {

        "마감된 퀴즈셋의 후보를 계산한다" {
            val quizSetId = saveOneToOneQuizSet(endDate = LocalDateTime.now().minusDays(1))
            val (a, b) = setupMatchingPair(quizSetId)

            matchingScheduler.generateForEndedQuizSets()

            matchCandidateRepository.findByOwnerMemberIdAndQuizSetId(a, quizSetId).map { it.otherMemberId } shouldBe listOf(b)
            matchCandidateRepository.findByOwnerMemberIdAndQuizSetId(b, quizSetId).map { it.otherMemberId } shouldBe listOf(a)
        }

        "아직 마감되지 않은 퀴즈셋은 건너뛴다" {
            val quizSetId = saveOneToOneQuizSet(endDate = LocalDateTime.now().plusDays(1))
            val (a, _) = setupMatchingPair(quizSetId)

            matchingScheduler.generateForEndedQuizSets()

            matchCandidateRepository.findByOwnerMemberIdAndQuizSetId(a, quizSetId) shouldHaveSize 0
        }

        "이미 후보가 계산된 마감 퀴즈셋은 다시 계산하지 않는다" {
            val quizSetId = saveOneToOneQuizSet(endDate = LocalDateTime.now().minusDays(1))
            setupMatchingPair(quizSetId)
            // 사전 계산 흔적(센티넬) → anti-join(findEndedQuizSetsWithoutCandidates)에서 제외되어 스킵되어야 한다
            matchCandidateRepository.save(
                MatchCandidateFixture.create(ownerMemberId = 999L, otherMemberId = 998L, quizSetId = quizSetId, score = 1.0),
            )

            matchingScheduler.generateForEndedQuizSets()

            // 재계산했다면 deleteByQuizSetId 로 센티넬이 사라졌을 것 → 그대로면 스킵된 것
            matchCandidateRepository.findByOwnerMemberIdAndQuizSetId(999L, quizSetId) shouldHaveSize 1
        }
    }
})
