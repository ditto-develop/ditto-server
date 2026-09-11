package com.ditto.domain.match.repository.querydsl

interface GroupMatchMemberRepositoryCustom {

    fun existsByMemberIdAndQuizSetId(memberId: Long, quizSetId: Long): Boolean

    /** 두 멤버가 같은 그룹 채팅방에 **함께 들어갔는지** — 양쪽 수락 + 그룹 성사. 성사 후 관계 판정. */
    fun existsSharedRoom(memberId: Long, otherMemberId: Long): Boolean

    /**
     * 두 멤버가 [quizSetId]의 같은 후보 그룹에 **아직 함께 남아 있는지** — 성사 전 관계 판정.
     * 어느 한쪽이라도 그 그룹을 거절했으면 같은 그룹이 될 일이 없으므로 제외한다.
     */
    fun existsSharedCandidateGroup(memberId: Long, otherMemberId: Long, quizSetId: Long): Boolean
}
