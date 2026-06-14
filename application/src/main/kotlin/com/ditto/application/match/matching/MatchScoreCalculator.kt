package com.ditto.application.match.matching

import kotlin.math.roundToInt

/**
 * 두 참여자의 매칭 점수를 계산한다.
 *
 * 매칭 점수 = (같은 quizId 에 같은 choiceId 를 고른 문항 수 ÷ 전체 문항 수) × 100, 소수점 1자리.
 * 같은 퀴즈셋을 완료(COMPLETED)한 참여자끼리이므로 두 사람의 답변 수(= 문항 수)는 동일하다.
 * 범위: 0.0 ~ 100.0 (겹치는 답이 하나도 없으면 0.0).
 * 점수와 함께 일치/전체 문항 수([MatchScore])를 반환해 scoreBreakdown 노출에 쓴다.
 */
object MatchScoreCalculator {

    fun calculate(participant1: MatchParticipant, participant2: MatchParticipant): MatchScore {
        val totalQuestionCount = participant1.answers.size
        if (totalQuestionCount == 0) return MatchScore(score = 0.0, matchedQuestionCount = 0, totalQuestionCount = 0)

        val matchedQuestionCount = participant1.answers.count { (quizId, choiceId) ->
            participant2.answers[quizId] == choiceId
        }

        val score = matchedQuestionCount.toDouble() / totalQuestionCount * 100
        return MatchScore(
            score = (score * 10).roundToInt() / 10.0, // 소수점 1자리
            matchedQuestionCount = matchedQuestionCount,
            totalQuestionCount = totalQuestionCount,
        )
    }
}
