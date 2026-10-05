package com.ditto.api.admin.quiz

import com.ditto.api.support.IntegrationTest
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.match.MatchCandidateFixture
import com.ditto.domain.match.PersonalMatchFixture
import com.ditto.domain.match.repository.MatchCandidateRepository
import com.ditto.domain.match.repository.PersonalMatchRepository
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.QuizAnswerFixture
import com.ditto.domain.quiz.QuizFixture
import com.ditto.domain.quiz.QuizProgressFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.entity.QuizSet
import com.ditto.domain.quiz.repository.QuizAnswerRepository
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import javax.sql.DataSource

class AdminQuizAnswerResetServiceTest(
    private val adminQuizAnswerResetService: AdminQuizAnswerResetService,
    private val quizSetRepository: QuizSetRepository,
    private val quizRepository: QuizRepository,
    private val quizAnswerRepository: QuizAnswerRepository,
    private val quizProgressRepository: QuizProgressRepository,
    private val memberRepository: MemberRepository,
    private val matchCandidateRepository: MatchCandidateRepository,
    private val personalMatchRepository: PersonalMatchRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    class AnsweredQuizSet(val quizSetId: Long, val realMemberId: Long, val dummyId: Long)

    // 문항 2개짜리 셋에 실회원 1명과 더미 1명이 둘 다 끝까지 푼 상태.
    fun saveAnsweredQuizSet(quizSet: QuizSet = QuizSetFixture.currentWeek()): AnsweredQuizSet {
        val quizSetId = quizSetRepository.save(quizSet).id
        val quizIds = (1..2).map { order ->
            quizRepository.save(QuizFixture.create(quizSetId = quizSetId, displayOrder = order)).id
        }
        val real = memberRepository.save(MemberFixture.create(nickname = "실회원", email = "real@example.com"))
        val dummy = memberRepository.save(MemberFixture.create(nickname = "dummy-male-0001", email = "d@dummy.local"))
        listOf(real.id, dummy.id).forEach { memberId ->
            val progress = QuizProgressFixture.create(memberId = memberId, quizSetId = quizSetId, totalCount = 2)
            repeat(2) { progress.recordAnswer() }
            quizProgressRepository.save(progress)
            quizIds.forEach { quizAnswerRepository.save(QuizAnswerFixture.create(memberId = memberId, quizId = it)) }
        }
        return AnsweredQuizSet(quizSetId, real.id, dummy.id)
    }

    fun saveMatchingRecords(quizSetId: Long, memberAId: Long, memberBId: Long) {
        matchCandidateRepository.save(MatchCandidateFixture.create(memberAId, memberBId, quizSetId))
        personalMatchRepository.save(PersonalMatchFixture.create(memberAId, memberBId, quizSetId))
    }

    "셋 전체 답·진행 초기화" - {
        "매칭 기록과 답·진행을 지우고 퀴즈셋과 문항은 남긴다" {
            val answered = saveAnsweredQuizSet()
            saveMatchingRecords(answered.quizSetId, answered.realMemberId, answered.dummyId)

            val summary = adminQuizAnswerResetService.resetAllAnswers(answered.quizSetId)

            summary.participantCount shouldBe 2
            summary.answerCount shouldBe 4
            quizAnswerRepository.findAll().shouldBeEmpty()
            quizProgressRepository.findAll().shouldBeEmpty()
            matchCandidateRepository.existsByQuizSetId(answered.quizSetId) shouldBe false
            personalMatchRepository.existsByQuizSetId(answered.quizSetId) shouldBe false
            quizSetRepository.existsById(answered.quizSetId) shouldBe true
            quizRepository.findByQuizSetIdOrderByDisplayOrderAsc(answered.quizSetId) shouldHaveSize 2
        }

        "지난 주 셋이면 거부하고 아무것도 지우지 않는다" {
            val answered = saveAnsweredQuizSet(QuizSetFixture.create())

            val exception = shouldThrow<WarnException> { adminQuizAnswerResetService.resetAllAnswers(answered.quizSetId) }

            exception.errorCode shouldBe ErrorCode.BAD_REQUEST
            quizProgressRepository.findAll() shouldHaveSize 2
        }

        "미리보기는 지워질 참여자 수와 그중 실회원 수를 센다" {
            val answered = saveAnsweredQuizSet()

            val preview = adminQuizAnswerResetService.previewAllAnswersReset(answered.quizSetId)

            preview.participantCount shouldBe 2
            preview.realParticipantCount shouldBe 1
        }
    }

    "회원별 답·진행 초기화" - {
        "그 회원의 답·진행만 지우고 다른 참여자는 남긴다" {
            val answered = saveAnsweredQuizSet()

            adminQuizAnswerResetService.resetMemberAnswers(answered.quizSetId, answered.dummyId)

            quizProgressRepository.findByMemberIdAndQuizSetId(answered.dummyId, answered.quizSetId) shouldBe null
            quizAnswerRepository.findAll().map { it.memberId }.toSet() shouldBe setOf(answered.realMemberId)
            quizProgressRepository.findAll() shouldHaveSize 1
        }

        "매칭 기록이 있는 셋이면 거부하고 아무것도 지우지 않는다" {
            val answered = saveAnsweredQuizSet()
            saveMatchingRecords(answered.quizSetId, answered.realMemberId, answered.dummyId)

            val exception = shouldThrow<WarnException> {
                adminQuizAnswerResetService.resetMemberAnswers(answered.quizSetId, answered.dummyId)
            }

            exception.errorCode shouldBe ErrorCode.BAD_REQUEST
            quizProgressRepository.findAll() shouldHaveSize 2
        }

        "참여하지 않은 회원이면 NOT_FOUND 예외가 발생한다" {
            val answered = saveAnsweredQuizSet()

            val exception = shouldThrow<WarnException> {
                adminQuizAnswerResetService.resetMemberAnswers(answered.quizSetId, 99999L)
            }

            exception.errorCode shouldBe ErrorCode.NOT_FOUND
        }

        "지난 주 셋이면 거부한다" {
            val answered = saveAnsweredQuizSet(QuizSetFixture.create())

            val exception = shouldThrow<WarnException> {
                adminQuizAnswerResetService.resetMemberAnswers(answered.quizSetId, answered.dummyId)
            }

            exception.errorCode shouldBe ErrorCode.BAD_REQUEST
        }
    }
})
