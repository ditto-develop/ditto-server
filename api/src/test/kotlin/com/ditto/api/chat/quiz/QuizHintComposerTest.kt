package com.ditto.api.chat.quiz

import com.ditto.api.quiz.stat.QuizAnswerSheet
import com.ditto.api.quiz.stat.QuizStatSnapshot
import com.ditto.domain.quiz.QuizChoiceFixture
import com.ditto.domain.quiz.QuizFixture
import com.ditto.domain.quiz.entity.QuizChoiceStat
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/** 문항 3개(10·20·30), 선택지는 문항 id × 10 + 1·2. */
private fun sheet(answers: Map<Long, Map<Long, Long>>) = QuizAnswerSheet(
    quizzes = listOf(10L, 20L, 30L).mapIndexed { i, id ->
        QuizFixture.create(id = id, displayOrder = i + 1, question = "Q$id", hintQuestion = "힌트$id")
    },
    choicesByQuizId = listOf(10L, 20L, 30L).associateWith { quizId ->
        listOf(1L, 2L).map { QuizChoiceFixture.create(id = quizId * 10 + it, quizId = quizId, content = "답${quizId * 10 + it}") }
    },
    choiceIdByMemberAndQuiz = answers,
)

private fun snapshot(percentByChoiceId: Map<Long, Int>) = QuizStatSnapshot(
    participantCount = 100,
    overlapThreshold = 2,
    choices = percentByChoiceId.map { (choiceId, percent) ->
        QuizChoiceStat.of(quizSetId = 1L, quizId = choiceId / 10, choiceId = choiceId, selectCount = percent, percent = percent)
    },
)

class QuizHintComposerTest : FreeSpec({

    "두 사람 방" - {
        "같은 답 중 전체 비율이 가장 낮은 문항을 SAME 으로 고른다" {
            val answers = mapOf(
                1L to mapOf(10L to 101L, 20L to 202L, 30L to 301L),
                2L to mapOf(10L to 101L, 20L to 202L, 30L to 302L),
            )

            val hint = QuizHintComposer.forPair(sheet(answers), listOf(1L, 2L), snapshot(mapOf(101L to 60, 202L to 18)))!!

            hint.quizId shouldBe 20L
            hint.kind shouldBe QuizHintKind.SAME
            hint.answer shouldBe "답202"
            hint.hintQuestion shouldBe "힌트20"
        }

        "같은 답이 없으면 첫 문항을 DIFFERENT 로 고르고 답은 비운다" {
            val answers = mapOf(
                1L to mapOf(10L to 101L, 20L to 201L, 30L to 301L),
                2L to mapOf(10L to 102L, 20L to 202L, 30L to 302L),
            )

            val hint = QuizHintComposer.forPair(sheet(answers), listOf(1L, 2L), snapshot = null)!!

            hint.quizId shouldBe 10L
            hint.kind shouldBe QuizHintKind.DIFFERENT
            hint.answer.shouldBeNull()
        }

        "스냅샷이 없으면 같은 답 중 문항 순서로 고른다" {
            val answers = mapOf(
                1L to mapOf(20L to 201L, 30L to 301L),
                2L to mapOf(20L to 201L, 30L to 301L),
            )

            QuizHintComposer.forPair(sheet(answers), listOf(1L, 2L), snapshot = null)!!.quizId shouldBe 20L
        }
    }

    "그룹 방" - {
        "만장일치 중 전체 비율이 가장 낮은 문항을 UNANIMOUS 로 고른다" {
            val answers = (1L..3L).associateWith { mapOf(10L to 101L, 20L to 202L, 30L to if (it == 1L) 301L else 302L) }

            val hint = QuizHintComposer.forGroup(sheet(answers), listOf(1L, 2L, 3L), snapshot(mapOf(101L to 40, 202L to 30)))!!

            hint.quizId shouldBe 20L
            hint.kind shouldBe QuizHintKind.UNANIMOUS
            hint.answer shouldBe "답202"
        }

        "만장일치가 없으면 인원 차가 가장 작은 문항을 SPLIT 으로 고른다(동률은 문항 순서)" {
            // 4명: 문항 10은 3:1, 문항 20은 2:2, 문항 30은 2:2 → 20
            val answers = mapOf(
                1L to mapOf(10L to 101L, 20L to 201L, 30L to 301L),
                2L to mapOf(10L to 101L, 20L to 201L, 30L to 301L),
                3L to mapOf(10L to 101L, 20L to 202L, 30L to 302L),
                4L to mapOf(10L to 102L, 20L to 202L, 30L to 302L),
            )

            val hint = QuizHintComposer.forGroup(sheet(answers), listOf(1L, 2L, 3L, 4L), snapshot = null)!!

            hint.quizId shouldBe 20L
            hint.kind shouldBe QuizHintKind.SPLIT
            hint.answer.shouldBeNull()
        }
    }

    "문항이 없으면 힌트를 만들지 않는다" {
        val empty = QuizAnswerSheet(emptyList(), emptyMap(), emptyMap())

        QuizHintComposer.forPair(empty, listOf(1L, 2L), null).shouldBeNull()
        QuizHintComposer.forGroup(empty, listOf(1L, 2L), null).shouldBeNull()
    }
})
