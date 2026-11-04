package com.ditto.api.chat.quiz

import com.ditto.api.quiz.stat.QuizAnswerSheet
import com.ditto.api.quiz.stat.QuizStatSnapshot
import com.ditto.domain.quiz.entity.Quiz

/**
 * 첫 대화 힌트로 쓸 문항을 고른다(IMP-1 BE-4). DB·시각을 모르는 순수 규칙이다.
 *
 * - 두 사람 방: ① 같은 답 중 그 답의 전체 비율이 가장 낮은 문항(SAME) ② 없으면 첫 문항(DIFFERENT, '갈렸어요')
 * - 그룹 방: ① 만장일치 중 그 답의 전체 비율이 가장 낮은 문항(UNANIMOUS) ② 없으면 인원 차가 가장 작은 문항(SPLIT)
 * - 동률은 문항 순서.
 *
 * 스냅샷이 없으면(마감 전) 비율을 모두 0으로 보고 문항 순서로 고른다.
 */
object QuizHintComposer {

    fun forPair(sheet: QuizAnswerSheet, memberIds: List<Long>, snapshot: QuizStatSnapshot?): QuizHintContent? {
        if (sheet.quizzes.isEmpty()) return null
        val sharedChoiceByQuiz = sheet.quizzes.associateWith { quiz ->
            memberIds.map { sheet.choiceOf(it, quiz.id) }.distinct().singleOrNull()
        }
        val rarestShared = sharedChoiceByQuiz.entries
            .filter { it.value != null }
            .minWithOrNull(compareBy({ snapshot?.percentOf(it.value!!) ?: 0 }, { it.key.displayOrder }))
        if (rarestShared != null) {
            return content(rarestShared.key, QuizHintKind.SAME, sheet.choiceContent(rarestShared.key, rarestShared.value!!))
        }
        return content(sheet.quizzes.first(), QuizHintKind.DIFFERENT, answer = null)
    }

    fun forGroup(sheet: QuizAnswerSheet, memberIds: List<Long>, snapshot: QuizStatSnapshot?): QuizHintContent? {
        if (sheet.quizzes.isEmpty()) return null
        val spreads = sheet.quizzes.associateWith { sheet.spreadAmong(it.id, memberIds) }

        val rarestUnanimous = spreads.filter { it.value.unanimous }.keys
            .map { quiz -> quiz to sheet.choicesOf(quiz.id)[spreads.getValue(quiz).counts.indexOfFirst { it > 0 }].id }
            .minWithOrNull(compareBy({ snapshot?.percentOf(it.second) ?: 0 }, { it.first.displayOrder }))
        if (rarestUnanimous != null) {
            val (quiz, choiceId) = rarestUnanimous
            return content(quiz, QuizHintKind.UNANIMOUS, sheet.choiceContent(quiz, choiceId))
        }
        val closest = spreads.entries.minWith(compareBy({ it.value.gap }, { it.key.displayOrder })).key
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
