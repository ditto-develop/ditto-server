package com.ditto.api.chat.dto

import java.time.LocalDateTime

/**
 * 그룹 방의 '우리 그룹의 답변'(CH-03·04). 방에 남아 있는 멤버 기준 실시간 분포다.
 *
 * **누가 무엇을 골랐는지 잇는 필드는 넣지 않는다**(인원 수만). 아바타는 기존 방 멤버 API 로 그린다.
 */
data class QuizDistributionResponse(
    val quizSetId: Long,
    /** 방에 남아 있는 멤버 수(나간 사람 제외). */
    val memberCount: Int,
    val totalCount: Int,
    /** 배너 '다 같은 답 N' */
    val unanimousCount: Int,
    /** 배너 '반반 M' = totalCount − unanimousCount */
    val splitCount: Int,
    /** 상세 '반반 갈린 질문' — 만장일치 제외, 인원 차가 가장 작은 순, 최대 3개, 동률은 문항 순서. */
    val splitQuizIds: List<Long>,
    val canSendCard: Boolean,
    val chatOpensAt: LocalDateTime,
    val items: List<QuizDistributionItem>,
)

data class QuizDistributionItem(
    val quizId: Long,
    val order: Int,
    val question: String,
    val choices: List<QuizDistributionChoice>,
    val myChoiceId: Long?,
    val unanimous: Boolean,
    val hintQuestion: String?,
    val sent: Boolean,
)

data class QuizDistributionChoice(
    val choiceId: Long,
    val content: String,
    val count: Int,
)
