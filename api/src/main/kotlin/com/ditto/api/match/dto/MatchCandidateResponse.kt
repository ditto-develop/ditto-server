package com.ditto.api.match.dto

import com.ditto.api.match.matching.MatchScore
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.ErrorException
import com.ditto.domain.member.entity.Member
import com.ditto.domain.quiz.entity.MatchingType
import com.ditto.domain.system.OperationWeek
import java.time.LocalDate

/**
 * 1:1 매칭 추천 후보 목록 응답.
 * 회원이 **이번 운영 주에** 완주한 1:1 퀴즈셋에서 노출받는 후보들을 매칭 점수 내림차순으로 담는다.
 *
 * 주차를 함께 내려준다 — FE 홈이 `GET /api/v1/system/state` 와 대조해 어느 트랙이 이번 주 것인지
 * 가린다. 식별자는 [weekStartedOn](그 주 월요일)이고 year/month/week 는 파생 표시값이다(ADR 0010).
 */
data class MatchCandidateResponse(
    val quizSetId: Long,
    val weekStartedOn: LocalDate,
    val year: Int,
    val month: Int,
    val week: Int,
    val matchingType: MatchingType,
    val algorithmVersion: String,
    val candidates: List<Candidate>,
) {
    companion object {
        fun of(
            quizSetId: Long,
            operationWeek: OperationWeek,
            matchingType: MatchingType,
            algorithmVersion: String,
            candidates: List<Candidate>,
        ) = MatchCandidateResponse(
            quizSetId = quizSetId,
            weekStartedOn = operationWeek.startedOn,
            year = operationWeek.year,
            month = operationWeek.month,
            week = operationWeek.weekOfMonth,
            matchingType = matchingType,
            algorithmVersion = algorithmVersion,
            candidates = candidates,
        )
    }
}

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
) {
    companion object {
        /**
         * 회원 프로필과 나와의 점수로 후보 카드를 만든다. 1:1 후보와 그룹 구성원이 같은 카드를 쓴다.
         *
         * location·caricature 는 가입 완료 시 필수값이라 후보(ACTIVE 회원)에는 항상 존재해야 한다.
         * 없으면 데이터 정합성 오류이므로 명시적으로 예외를 던진다.
         */
        fun of(member: Member, introduction: String?, matchScore: MatchScore): Candidate {
            val location = member.location
                ?: throw ErrorException(ErrorCode.INTERNAL_ERROR, "후보 회원의 사는곳이 비어 있습니다: memberId=${member.id}")
            val profileImage = member.caricature
                ?: throw ErrorException(ErrorCode.INTERNAL_ERROR, "후보 회원의 캐리커쳐가 비어 있습니다: memberId=${member.id}")

            return Candidate(
                userId = member.id,
                nickname = member.nickname,
                gender = member.gender?.name,
                age = member.age,
                introduction = introduction,
                location = location.code,
                profileImageUrl = profileImage,
                matchRate = matchScore.score,
                scoreBreakdown = ScoreSummary.of(matchScore),
            )
        }
    }
}

data class ScoreSummary(
    /** 퀴즈 답변 일치율 (0~100). 현재는 matchRate 와 동일 값 */
    val quizMatchRate: Double,
    val matchedQuestions: Int,
    val totalQuestions: Int,
    /** 매칭 사유 문구. 현재는 일치 문항 수 기반 합성 문장 */
    val reasons: List<String>,
) {
    companion object {
        fun of(matchScore: MatchScore): ScoreSummary = ScoreSummary(
            quizMatchRate = matchScore.score,
            matchedQuestions = matchScore.matchedQuestionCount,
            totalQuestions = matchScore.totalQuestionCount,
            reasons = listOf(
                "전체 ${matchScore.totalQuestionCount}문항 중 ${matchScore.matchedQuestionCount}문항이 일치했어요",
            ),
        )
    }
}
