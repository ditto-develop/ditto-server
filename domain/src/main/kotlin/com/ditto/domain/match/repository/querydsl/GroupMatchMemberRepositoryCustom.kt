package com.ditto.domain.match.repository.querydsl

interface GroupMatchMemberRepositoryCustom {

    fun existsByMemberIdAndQuizSetId(memberId: Long, quizSetId: Long): Boolean

    /** 두 멤버가 같은 그룹 채팅방(roomId)에 함께 참여한 적이 있는지 여부 */
    fun existsSharedRoom(memberId: Long, otherMemberId: Long): Boolean
}
