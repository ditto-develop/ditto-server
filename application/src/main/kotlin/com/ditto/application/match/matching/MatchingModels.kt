package com.ditto.application.match.matching

import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.GenderPreference

/**
 * 매칭 알고리즘 입력: 한 참여자와 그 답변·성별·나이·성별선호.
 *
 * 성별/나이 미상(Member.gender/age == null) 회원은 성별·나이 기반 매칭이 불가하므로 후보 풀 구성 단계
 * ([com.ditto.application.match.service.MatchmakingService])에서 제외된다. 따라서 여기서 gender·age 는 항상 존재한다.
 *
 * @property answers quizId -> choiceId (같은 quizId에 같은 choiceId 면 답변 일치)
 * @property preferredGender 이 퀴즈에서의 매칭 성별 선호
 */
data class MatchParticipant(
    val memberId: Long,
    val answers: Map<Long, Long>,
    val gender: Gender,
    val age: Int,
    val preferredGender: GenderPreference = GenderPreference.ANY,
) {
    /** 이 참여자가 [other]의 성별을 매칭 대상으로 받아들이는가. */
    fun accepts(other: MatchParticipant): Boolean =
        other.gender in preferredGender.targetGenders(gender)

    /** 두 참여자가 서로의 성별 선호를 모두 충족하는가(상호 호환). 1:1 매칭 페어 성립 조건. */
    fun isMutuallyCompatibleWith(other: MatchParticipant): Boolean =
        accepts(other) && other.accepts(this)
}

/**
 * 매칭 점수 계산 결과. 점수와 그 근거(일치/전체 문항 수)를 함께 담는다.
 *
 * @property score 매칭 점수 (0.0 ~ 100.0, 소수점 1자리)
 * @property matchedQuestionCount 같은 답을 고른 문항 수
 * @property totalQuestionCount 비교한 전체 문항 수
 */
data class MatchScore(
    val score: Double,
    val matchedQuestionCount: Int,
    val totalQuestionCount: Int,
)

/**
 * 점수화된 페어. memberId1 < memberId2 로 정규화하여 방향 무관하게 동일 페어를 표현한다.
 *
 * @property score 매칭 점수 (0.0 ~ 100.0, 소수점 1자리)
 * @property matchedQuestionCount 같은 답을 고른 문항 수 (scoreBreakdown 노출용)
 * @property totalQuestionCount 비교한 전체 문항 수
 */
data class ScoredDuo(
    val memberId1: Long,
    val memberId2: Long,
    val score: Double,
    val matchedQuestionCount: Int,
    val totalQuestionCount: Int,
) {
    companion object {
        fun of(
            memberA: Long,
            memberB: Long,
            score: Double,
            matchedQuestionCount: Int,
            totalQuestionCount: Int,
        ): ScoredDuo =
            ScoredDuo(
                memberId1 = minOf(memberA, memberB),
                memberId2 = maxOf(memberA, memberB),
                score = score,
                matchedQuestionCount = matchedQuestionCount,
                totalQuestionCount = totalQuestionCount,
            )
    }
}
