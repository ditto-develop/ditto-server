package com.ditto.api.quiz

import com.ditto.api.quiz.dto.QuizHistoryPhase
import com.ditto.api.quiz.service.QuizHistoryService
import com.ditto.api.support.IntegrationTest
import com.ditto.api.support.QuizSeeder
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.common.serialization.ObjectMapperFactory
import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.MemberStatus
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.quiz.repository.QuizAnswerRepository
import com.ditto.domain.quiz.repository.QuizChoiceRepository
import com.ditto.domain.quiz.repository.QuizProgressRepository
import com.ditto.domain.quiz.repository.QuizRepository
import com.ditto.domain.quiz.repository.QuizSetRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import javax.sql.DataSource

/** 이번 주(11/2 주) 수요일 저녁 — 11/2 주 셋은 진행 중, 10/26 주 셋은 결과 공개 후다. */
private val WEDNESDAY_EVENING = LocalDateTime.of(2026, 11, 4, 20, 0)

class QuizHistoryServiceTest(
    private val quizHistoryService: QuizHistoryService,
    private val memberRepository: MemberRepository,
    quizSetRepository: QuizSetRepository,
    quizRepository: QuizRepository,
    quizChoiceRepository: QuizChoiceRepository,
    quizAnswerRepository: QuizAnswerRepository,
    quizProgressRepository: QuizProgressRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    val seeder = QuizSeeder(quizSetRepository, quizRepository, quizChoiceRepository, quizAnswerRepository, quizProgressRepository)
    var seq = 0
    val objectMapper = ObjectMapperFactory.create()

    fun member() = memberRepository.save(MemberFixture.create(nickname = "회원${seq++}", status = MemberStatus.ACTIVE)).id

    "기록 탭" - {
        "완주한 주만 최신순으로 주고, 결과가 공개된 주만 소수 의견을 합산한다" {
            val me = member()
            val closed = seeder.quizSet(weekStartedOn = QuizSeeder.CLOSED_WEEK)
            val current = seeder.quizSet(weekStartedOn = QuizSeeder.CLOSED_WEEK.plusWeeks(1), questions = 2)
            val stopped = seeder.quizSet(weekStartedOn = QuizSeeder.CLOSED_WEEK.minusWeeks(1))
            // 20명 중 나만 문항 1에서 2번 → 소수 의견 1개
            seeder.answer(me, closed, listOf(2, 1, 1))
            repeat(19) { seeder.answer(member(), closed, listOf(1, 1, 1)) }
            seeder.answer(me, current, listOf(1, 1))
            seeder.answer(me, stopped, listOf(1))

            val history = quizHistoryService.getHistory(me, WEDNESDAY_EVENING)

            history.weeks.map { it.quizSetId } shouldBe listOf(current.quizSetId, closed.quizSetId)
            history.weeks[0].minorityCount.shouldBeNull()
            history.weeks[1].minorityCount shouldBe 1
            history.summary.participatedWeeks shouldBe 2
            history.summary.answeredQuestions shouldBe 5
            history.summary.minorityCount shouldBe 1
            history.currentWeek!!.quizSetId shouldBe current.quizSetId
            history.currentWeek!!.resultOpen shouldBe false
            history.currentWeek!!.resultOpensAt shouldBe LocalDateTime.of(2026, 11, 5, 0, 0)
        }

        "이번 주를 완주하지 않았으면 currentWeek 는 null 이다" {
            val me = member()
            seeder.answer(me, seeder.quizSet(weekStartedOn = QuizSeeder.CLOSED_WEEK), listOf(1, 1, 1))

            quizHistoryService.getHistory(me, WEDNESDAY_EVENING).currentWeek.shouldBeNull()
        }
    }

    "주차 상세" - {
        "진행 중이면 비율·소수 의견·결과를 응답 JSON 에 싣지 않는다" {
            val me = member()
            val current = seeder.quizSet(weekStartedOn = QuizSeeder.CLOSED_WEEK.plusWeeks(1), questions = 2)
            seeder.answer(me, current, listOf(1, 2))
            seeder.answer(member(), current, listOf(1, 1))

            val detail = quizHistoryService.getDetail(me, current.quizSetId, WEDNESDAY_EVENING)

            detail.phase shouldBe QuizHistoryPhase.IN_PROGRESS
            detail.participantCount shouldBe 2L
            detail.answers.map { it.myChoiceId } shouldBe listOf(current.choice(1, 1), current.choice(2, 2))
            val json = objectMapper.readTree(objectMapper.writeValueAsString(detail))
            json.has("result") shouldBe false
            json["answers"][0]["choices"][0].has("percent") shouldBe false
            json["answers"][0]["choices"][0].has("minority") shouldBe false
        }

        "마감 뒤에는 스냅샷 비율·소수 의견·겹친 사람 수를 준다" {
            val me = member()
            val closed = seeder.quizSet(weekStartedOn = QuizSeeder.CLOSED_WEEK, questions = 3)
            seeder.answer(me, closed, listOf(2, 1, 1))
            repeat(19) { seeder.answer(member(), closed, listOf(1, 1, 1)) }

            val detail = quizHistoryService.getDetail(me, closed.quizSetId, WEDNESDAY_EVENING)

            detail.phase shouldBe QuizHistoryPhase.CLOSED
            detail.participantCount shouldBe 20L
            detail.answers[0].choices.map { it.percent } shouldBe listOf(95, 5)
            detail.answers[0].choices[1].minority shouldBe true
            val result = detail.result.shouldNotBeNull()
            result.overlapThreshold shouldBe 2
            result.overlapCount shouldBe 19
            result.minorityCount shouldBe 1
        }

        "끝까지 풀지 않은 셋은 기록이 없다" {
            val me = member()
            val set = seeder.quizSet()
            seeder.answer(me, set, listOf(1))

            shouldThrow<WarnException> { quizHistoryService.getDetail(me, set.quizSetId, WEDNESDAY_EVENING) }
                .errorCode shouldBe ErrorCode.QUIZ_HISTORY_NOT_FOUND
            shouldThrow<WarnException> { quizHistoryService.getDetail(me, 9_999L, WEDNESDAY_EVENING) }
                .errorCode shouldBe ErrorCode.QUIZ_HISTORY_NOT_FOUND
        }
    }
})
