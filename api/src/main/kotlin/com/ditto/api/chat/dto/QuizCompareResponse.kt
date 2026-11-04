package com.ditto.api.chat.dto

import java.time.LocalDateTime

/** 1:1·재매칭 방의 '우리의 답변'(CH-01·02). 배너 숫자(같은 답 N · 다른 답 M)도 이 응답으로 그린다. */
data class QuizCompareResponse(
    val quizSetId: Long,
    val totalCount: Int,
    val sameCount: Int,
    val diffCount: Int,
    /** 같은 답 중 소수 의견이고 내 선택 비율이 가장 낮은 문항(동률은 문항 순서). 없으면 null. */
    val highlightQuizId: Long?,
    /** 방이 열려 있고(ACTIVE) 질문 카드 기능이 켜져 있을 때만 true. */
    val canSendCard: Boolean,
    /** 방 개방 시각. 열리기 전 딤드 버튼 문구("{개방 시각}부터 보낼 수 있어요")에 쓴다. */
    val chatOpensAt: LocalDateTime,
    val items: List<QuizCompareItem>,
)

data class QuizCompareItem(
    val quizId: Long,
    val order: Int,
    val question: String,
    val choices: List<QuizComparePercentChoice>,
    val myChoiceId: Long?,
    val peerChoiceId: Long?,
    val same: Boolean,
    /** 내 선택 기준 소수 의견. */
    val minority: Boolean,
    /** 문항별 대화 힌트 질문. 비어 있으면 null → FE 공통 문구. */
    val hintQuestion: String?,
    /** 이 방에 이미 카드로 올린 문항. */
    val sent: Boolean,
)

data class QuizComparePercentChoice(
    val choiceId: Long,
    val content: String,
    /** 그 주 전체 완주자 기준(마감 스냅샷). */
    val percent: Int,
)
