package com.ditto.domain.quiz.repository.querydsl

interface QuizProgressRepositoryCustom {
    fun deleteByMemberIdAndQuizSetIds(memberId: Long, quizSetIds: List<Long>)

    /**
     * 두 회원이 **함께 완주(COMPLETED)** 한 가장 최근 퀴즈셋 id. 함께 완주한 셋이 없으면 null.
     *
     * 답변 일치 비교의 기준 퀴즈셋을 고르는 데 쓴다 — 둘 다 완주해야 문항 수가 같아 비교가 성립한다.
     * "최근"은 id 내림차순으로 판단한다(auto-increment라 생성 순서 = 시간 순서).
     */
    fun findLatestQuizSetIdCompletedByBoth(memberId: Long, otherMemberId: Long): Long?
}
