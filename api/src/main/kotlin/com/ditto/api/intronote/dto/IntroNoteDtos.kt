package com.ditto.api.intronote.dto

import jakarta.validation.constraints.Size

/**
 * 소개노트 답변 저장 요청. 빈 문자열(부분 저장) 허용.
 */
data class SaveIntroNoteRequest(
    @field:Size(max = 500, message = "답변은 최대 500자까지 가능합니다.")
    val answer: String = "",
)

/**
 * 질문 하나에 대한 소개노트 응답. 미작성 질문은 answer가 빈 문자열이다.
 */
data class IntroNoteResponse(
    val questionCode: String,
    val question: String,
    val answer: String,
)

/**
 * 회원의 전체 소개노트 응답. 고정 질문 순서대로 항상 전체를 반환한다.
 */
data class IntroNotesResponse(
    val answers: List<IntroNoteResponse>,
    val completedCount: Int,
)
