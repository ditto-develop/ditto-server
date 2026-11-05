package com.ditto.api.quiz.dto

import com.ditto.domain.quiz.entity.MatchingType
import com.fasterxml.jackson.annotation.JsonInclude
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 주차 상세(QH-02 진행 중 / QH-03 결과). [phase]로 갈린다.
 *
 * 진행 중에는 선택지 비율·소수 의견·결과를 **응답에 싣지 않는다**(키 자체가 없다) — 화면이 안 그리는 것만으로는
 * 응답에 값이 실려 나가 결과를 미리 볼 수 있다.
 */
data class QuizHistoryDetailResponse(
    val quizSetId: Long,
    val title: String,
    val matchingType: MatchingType,
    val weekStartedOn: LocalDate,
    val phase: QuizHistoryPhase,
    val resultOpensAt: LocalDateTime,
    /** 진행 중: 홈과 같은 지금까지 완주자 수. 결과 공개 후: 스냅샷의 최종 참여자 수. */
    val participantCount: Long,
    val answers: List<QuizHistoryAnswer>,
    @field:JsonInclude(JsonInclude.Include.NON_NULL)
    val result: QuizHistoryResult?,
)

enum class QuizHistoryPhase { IN_PROGRESS, CLOSED }

data class QuizHistoryAnswer(
    val quizId: Long,
    val order: Int,
    val question: String,
    val choices: List<QuizHistoryChoice>,
    val myChoiceId: Long?,
)

data class QuizHistoryChoice(
    val choiceId: Long,
    val content: String,
    @field:JsonInclude(JsonInclude.Include.NON_NULL)
    val percent: Int?,
    @field:JsonInclude(JsonInclude.Include.NON_NULL)
    val minority: Boolean?,
)

data class QuizHistoryResult(
    /** 문구 '나와 {overlapThreshold}개 이상 답이 겹친 사람'. */
    val overlapThreshold: Int,
    val overlapCount: Int,
    val minorityCount: Int,
)
