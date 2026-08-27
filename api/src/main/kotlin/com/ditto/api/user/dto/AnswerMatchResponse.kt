package com.ditto.api.user.dto

/**
 * "나와 같은 답" 비교 요약. 상대가 무엇을 골랐는지는 담지 않는다 —
 * 화면(피그마 `3.1 매칭 & 프로필`)이 쓰는 것은 일치 개수와 그에 따른 등급 라벨(그룹은 평균 일치 수)뿐이고,
 * 라벨 문구·평균 계산은 FE가 한다.
 */
data class AnswerMatchResponse(
    /** 비교 기준 퀴즈셋 — 두 사람이 함께 완주한 가장 최근 셋. 없으면 null이고 나머지는 0이다. */
    val quizSetId: Long?,
    val matchedCount: Int,
    val totalCount: Int,
    /** 일치율 0~100 (소수점 1자리). 매칭 점수와 같은 계산이라 두 화면의 수치가 어긋나지 않는다. */
    val matchRate: Double,
) {
    companion object {
        fun empty(): AnswerMatchResponse = AnswerMatchResponse(
            quizSetId = null,
            matchedCount = 0,
            totalCount = 0,
            matchRate = 0.0,
        )
    }
}
