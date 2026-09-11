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
 * 소개노트 응답. 항상 고정 질문 순서대로 담는다.
 *
 * 본인·매칭 성사 상대는 전체 질문이 오고, **성사 전 매칭 후보**는 미리보기 3문항만 온다
 * (무작위 2문항 + `one-word`). [completedCount] 는 이 응답에 담긴 답변 중 작성된 수다.
 */
data class IntroNotesResponse(
    val answers: List<IntroNoteResponse>,
    val completedCount: Int,
)
