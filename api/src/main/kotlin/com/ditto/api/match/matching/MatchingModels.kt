package com.ditto.api.match.matching

import com.ditto.domain.member.entity.Gender
import com.ditto.domain.member.entity.GenderPreference

/**
 * 매칭 알고리즘 입력: 한 참여자와 그 답변·성별·나이·성별선호.
 *
 * 성별·나이는 소셜 로그인 동의 항목이라 **없을 수 있다**. 자격 판단은 그 조건을 쓰는 쪽이 한다 —
 * 1:1은 성별 상호 선호와 나이차를 따지므로 미상이면 페어가 성립하지 않고, 그룹은 두 조건을 쓰지 않아
 * 미상 회원도 그대로 후보가 된다.
 *
 * @property answers quizId -> choiceId (같은 quizId에 같은 choiceId 면 답변 일치)
 * @property preferredGender 이 퀴즈에서의 매칭 성별 선호
 */
data class MatchParticipant(
    val memberId: Long,
    val answers: Map<Long, Long>,
    val gender: Gender? = null,
    val age: Int? = null,
    val preferredGender: GenderPreference = GenderPreference.ANY,
    val blockedMemberIds: Set<Long> = emptySet(),
) {
    /** 이 참여자가 [other]의 성별을 매칭 대상으로 받아들이는가. 어느 한쪽이라도 성별 미상이면 판단할 수 없다. */
    fun accepts(other: MatchParticipant): Boolean {
        val myGender = gender ?: return false
        val otherGender = other.gender ?: return false
        return otherGender in preferredGender.targetGenders(myGender)
    }

    /** 두 참여자가 서로의 성별 선호를 모두 충족하는가(상호 호환). 1:1 매칭 페어 성립 조건. */
    fun isMutuallyCompatibleWith(other: MatchParticipant): Boolean =
        accepts(other) && other.accepts(this)

    /**
     * 둘 사이에 차단이 있는가. 차단은 방향과 무관하게 페어를 깨야 해서 양쪽을 모두 본다
     * ([blockedMemberIds]가 이미 양방향으로 채워지지만, 대칭 판정을 이 함수 안에 두어
     * 채우는 쪽 실수에 의존하지 않게 한다).
     */
    fun isBlockedWith(other: MatchParticipant): Boolean =
        other.memberId in blockedMemberIds || memberId in other.blockedMemberIds
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
 * 점수화된 매칭 한 건. 1:1은 2명, 그룹은 3~6명으로 **인원 수만 다르고** 선발 파이프라인
 * ([TopRatioSelector]·[HardLimitApplier])이 다루는 방식은 같아 한 타입으로 표현한다.
 *
 * [memberIds]가 동일성 기준이다 — `Set` 비교라 순서가 달라도 같은 매칭으로 본다.
 * 1:1의 (A,B)=(B,A) 정규화와 그룹의 "씨앗이 달라도 같은 조합이면 중복" 판정이 둘 다 이걸로 해결된다.
 *
 * @property score 매칭 점수 (0.0 ~ 100.0). 그룹은 구성원 모든 페어 점수의 평균.
 * @property matchedQuestionCount 같은 답을 고른 문항 수. **1:1 전용**
 * @property totalQuestionCount 비교한 전체 문항 수. **1:1 전용**
 */
data class ScoredMatch private constructor(
    val memberIds: Set<Long>,
    val score: Double,
    val matchedQuestionCount: Int?,
    val totalQuestionCount: Int?,
) {
    companion object {
        /** 1:1 페어. 점수와 그 근거(문항 수)가 항상 함께 채워지도록 [MatchScore]째로 받는다. */
        fun duo(memberAId: Long, memberBId: Long, matchScore: MatchScore): ScoredMatch =
            ScoredMatch(
                memberIds = setOf(memberAId, memberBId),
                score = matchScore.score,
                matchedQuestionCount = matchScore.matchedQuestionCount,
                totalQuestionCount = matchScore.totalQuestionCount,
            )

        /**
         * 그룹. 점수가 구성원 모든 페어 점수의 **평균**이라 대응하는 정수 문항 수가 없어
         * 근거 필드를 비운다 — 그룹 화면은 평균 점수만 보여준다.
         */
        fun group(memberIds: Set<Long>, score: Double): ScoredMatch =
            ScoredMatch(
                memberIds = memberIds,
                score = score,
                matchedQuestionCount = null,
                totalQuestionCount = null,
            )
    }
}
