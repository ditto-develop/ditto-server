package com.ditto.domain.quiz.repository

import com.ditto.domain.member.MemberFixture
import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.MemberRole
import com.ditto.domain.member.repository.MemberRepository
import com.ditto.domain.member.repository.querydsl.MemberFilter
import com.ditto.domain.quiz.QuizAnswerFixture
import com.ditto.domain.quiz.QuizChoiceFixture
import com.ditto.domain.quiz.QuizFixture
import com.ditto.domain.quiz.QuizSetFixture
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.quiz.entity.QuizChoice
import com.ditto.domain.quiz.entity.QuizSet
import com.ditto.domain.support.IntegrationTest
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import javax.sql.DataSource

private val WEEK = LocalDate.of(2026, 10, 19)

class QuizAnswerStatsQueryTest(
    private val quizAnswerRepository: QuizAnswerRepository,
    private val quizSetRepository: QuizSetRepository,
    private val quizRepository: QuizRepository,
    private val quizChoiceRepository: QuizChoiceRepository,
    private val memberRepository: MemberRepository,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    var memberIndex = 0

    fun saveMember(gender: Gender = Gender.MALE, role: MemberRole = MemberRole.USER): Long {
        memberIndex += 1
        val nickname = "m$memberIndex"
        return memberRepository.save(
            MemberFixture.create(nickname = nickname, email = "$nickname@ditto.pics", gender = gender, role = role),
        ).id
    }

    fun saveQuizSet(
        weekStartedOn: LocalDate = WEEK,
        matchingType: MatchingType = MatchingType.ONE_TO_ONE,
        isActive: Boolean = true,
    ): QuizSet = quizSetRepository.save(
        QuizSetFixture.create(
            startDate = weekStartedOn.atStartOfDay(),
            endDate = weekStartedOn.plusDays(2).atTime(23, 59, 59),
            matchingType = matchingType,
            isActive = isActive,
        ),
    )

    // 문항 하나와 선택지 A·B 를 만든다.
    fun saveQuestion(quizSet: QuizSet, displayOrder: Int = 1): Pair<QuizChoice, QuizChoice> {
        val quiz = quizRepository.save(
            QuizFixture.create(quizSetId = quizSet.id, question = "질문$displayOrder", displayOrder = displayOrder),
        )
        val (choiceA, choiceB) = listOf("A", "B").mapIndexed { index, content ->
            val choice = QuizChoiceFixture.create(quizId = quiz.id, content = content, displayOrder = index + 1)
            quizChoiceRepository.save(choice)
        }
        return choiceA to choiceB
    }

    fun answer(memberId: Long, choice: QuizChoice) {
        quizAnswerRepository.save(
            QuizAnswerFixture.create(memberId = memberId, quizId = choice.quizId, choiceId = choice.id),
        )
    }

    "countAnswersPerChoiceInWeek" - {
        "선택지마다 답 수를 문항·선택지 순서로 주고 답이 없는 선택지는 0이다" {
            val quizSet = saveQuizSet()
            val (secondA, secondB) = saveQuestion(quizSet, displayOrder = 2)
            val (firstA, _) = saveQuestion(quizSet, displayOrder = 1)
            answer(saveMember(), firstA)
            answer(saveMember(), firstA)
            answer(saveMember(), secondB)

            val counts = quizAnswerRepository.countAnswersPerChoiceInWeek(WEEK, null, MemberFilter.NONE)

            counts.map { Triple(it.question, it.choiceContent, it.count) } shouldBe listOf(
                Triple("질문1", "A", 2L),
                Triple("질문1", "B", 0L),
                Triple("질문2", "A", 0L),
                Triple("질문2", "B", 1L),
            )
            counts.map { it.choiceId }.takeLast(2) shouldBe listOf(secondA.id, secondB.id)
        }

        "회원 조건에 안 맞는 답과 관리자의 답은 빼도 선택지 행은 남는다" {
            val (choiceA, choiceB) = saveQuestion(saveQuizSet())
            answer(saveMember(Gender.FEMALE), choiceA)
            answer(saveMember(Gender.MALE), choiceB)
            answer(saveMember(Gender.FEMALE, role = MemberRole.ADMIN), choiceB)

            val femaleOnly = MemberFilter(gender = Gender.FEMALE)

            val counts = quizAnswerRepository.countAnswersPerChoiceInWeek(WEEK, null, femaleOnly)

            counts.map { it.choiceContent to it.count } shouldBe listOf("A" to 1L, "B" to 0L)
        }

        "비활성 셋·다른 주·다른 매칭 유형의 문항은 나오지 않는다" {
            saveQuestion(saveQuizSet(isActive = false))
            saveQuestion(saveQuizSet(weekStartedOn = WEEK.minusWeeks(1)))
            saveQuestion(saveQuizSet(matchingType = MatchingType.GROUP))

            quizAnswerRepository.countAnswersPerChoiceInWeek(WEEK, MatchingType.ONE_TO_ONE, MemberFilter.NONE)
                .shouldBeEmpty()
        }
    }
})
