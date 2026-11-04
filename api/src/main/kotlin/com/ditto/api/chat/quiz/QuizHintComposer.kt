package com.ditto.api.chat.quiz

import com.ditto.api.quiz.stat.QuizAnswerSheet
import com.ditto.api.quiz.stat.QuizStatSnapshot
import com.ditto.domain.quiz.entity.Quiz

/**
 * 첫 대화 힌트로 쓸 문항을 고른다(IMP-1 BE-4). DB·시각을 모르는 순수 규칙이다. 순서 규칙은 [QuizPickRules]에 있다.
 *
 * - 두 사람 방: ① 같은 답 중 그 답의 전체 비율이 가장 낮은 문항(SAME) ② 없으면 첫 문항(DIFFERENT, '갈렸어요')
 * - 그룹 방: ① 만장일치 중 그 답의 전체 비율이 가장 낮은 문항(UNANIMOUS) ② 없으면 인원 차가 가장 작은 문항(SPLIT)
 */
object QuizHintComposer {

    fun forPair(sheet: QuizAnswerSheet, memberIds: List<Long>, snapshot: QuizStatSnapshot?): QuizHintContent? {
        val first = sheet.quizzes.firstOrNull() ?: return null
        val shared = sheet.quizzes.mapNotNull { quiz ->
            memberIds.map { sheet.choiceOf(it, quiz.id) }.distinct().singleOrNull()
                ?.let { QuizPickRules.Candidate(quiz.displayOrder, it, quiz to it) }
        }
        val rarest = QuizPickRules.rarest(shared, snapshot)
            ?: return content(first, QuizHintKind.DIFFERENT, answer = null)
        val (quiz, choiceId) = rarest
        return content(quiz, QuizHintKind.SAME, sheet.choiceContent(quiz, choiceId))
    }

    fun forGroup(sheet: QuizAnswerSheet, memberIds: List<Long>, snapshot: QuizStatSnapshot?): QuizHintContent? {
        if (sheet.quizzes.isEmpty()) return null
        val spreads = sheet.quizzes.map { it to GroupAnswerSpread.of(sheet, it.id, memberIds) }

        val unanimous = spreads.mapNotNull { (quiz, spread) ->
            spread.unanimousChoiceId?.let { QuizPickRules.Candidate(quiz.displayOrder, it, quiz to it) }
        }
        QuizPickRules.rarest(unanimous, snapshot)?.let { (quiz, choiceId) ->
            return content(quiz, QuizHintKind.UNANIMOUS, sheet.choiceContent(quiz, choiceId))
        }
        val closest = QuizPickRules.closestSplits(spreads, { it.second.gap }, { it.first.displayOrder }).first().first
        return content(closest, QuizHintKind.SPLIT, answer = null)
    }

    private fun content(quiz: Quiz, kind: QuizHintKind, answer: String?) = QuizHintContent(
        quizId = quiz.id,
        kind = kind,
        question = quiz.question,
        answer = answer,
        hintQuestion = quiz.hintQuestion,
    )

    private fun QuizAnswerSheet.choiceContent(quiz: Quiz, choiceId: Long): String? =
        choicesOf(quiz.id).find { it.id == choiceId }?.content
}

/**
 * 힌트 메시지 본문(JSON). 문장이 아니라 재료만 담는다 — 문구는 보는 사람과 방 유형을 아는 FE 가 만든다.
 * 보낸 시점 값으로 굳는다(나중에 문항 문구가 바뀌어도 이미 남긴 힌트는 그대로다).
 */
data class QuizHintContent(
    val quizId: Long,
    val kind: QuizHintKind,
    val question: String,
    /** SAME·UNANIMOUS 일 때 함께 고른 답. DIFFERENT·SPLIT 은 null. */
    val answer: String?,
    val hintQuestion: String?,
)

enum class QuizHintKind { SAME, DIFFERENT, UNANIMOUS, SPLIT }
