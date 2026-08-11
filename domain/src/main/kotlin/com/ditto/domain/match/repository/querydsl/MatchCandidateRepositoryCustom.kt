package com.ditto.domain.match.repository.querydsl

interface MatchCandidateRepositoryCustom {

    /**
     * 해당 퀴즈셋에서 후보를 **받은** 회원 ID 목록(중복 제거).
     *
     * 후보 행은 (owner → other) 두 방향으로 저장되므로 owner 쪽만 모으면 "이번 주 볼 후보가 생긴 사람"이
     * 된다. 알림 적재가 이 목록을 쓴다 — 참여자 전원이 아니라 후보가 실제로 생긴 사람에게만 알린다.
     */
    fun findOwnerMemberIdsByQuizSetId(quizSetId: Long): List<Long>
}
