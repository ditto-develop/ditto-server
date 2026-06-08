package com.ditto.api.match.dto

import com.ditto.domain.quiz.entity.MatchingType

/**
 * 1:1 매칭 추천 후보 목록 응답.
 * 회원이 최근 완료한 1:1 퀴즈셋에서 노출받는 후보들을 매칭 점수 내림차순으로 담는다.
 */
data class MatchCandidateResponse(
    val quizSetId: Long,
    val matchingType: MatchingType,
    val algorithmVersion: String,
    val candidates: List<Candidate>,
)

data class Candidate(
    val userId: Long,
    val nickname: String,
    // gender·age 는 소셜 로그인(카카오) 동의 항목이라 가입 시 필수가 아니다 → null 가능
    val gender: String?,
    val age: Int?,
    /** 자기소개는 소개노트 ONE_WORD 답변으로 채운다. 미작성/공백이면 null */
    val introduction: String?,
    // location·profileImageUrl(캐리커쳐)은 가입 완료 시 필수값이라 항상 존재한다
    val location: String,
    val profileImageUrl: String,
    /** 매칭 점수 (0~100) */
    val matchRate: Double,
    val scoreBreakdown: ScoreSummary,
)

data class ScoreSummary(
    /** 퀴즈 답변 일치율 (0~100). 현재는 matchRate 와 동일 값 */
    val quizMatchRate: Double,
    val matchedQuestions: Int,
    val totalQuestions: Int,
    /** 매칭 사유 문구. 현재는 일치 문항 수 기반 합성 문장 */
    val reasons: List<String>,
)
