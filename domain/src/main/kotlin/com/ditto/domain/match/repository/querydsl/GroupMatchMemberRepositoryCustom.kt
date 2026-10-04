package com.ditto.domain.match.repository.querydsl

import java.time.LocalDate
import java.time.LocalDateTime

interface GroupMatchMemberRepositoryCustom {

    fun existsByMemberIdAndQuizSetId(memberId: Long, quizSetId: Long): Boolean

    /** [weekStartedOn] 주차의 아직 성사되지 않은 그룹에 수락해 둔 초대가 있는지. 탈퇴 제한 판단에 쓴다. */
    fun existsAcceptedInUnformedGroupOfWeek(memberId: Long, weekStartedOn: LocalDate): Boolean

    /** 두 멤버가 같은 그룹 채팅방에 **함께 들어갔는지** — 양쪽 수락 + 그룹 성사. 성사 후 관계 판정. */
    fun existsSharedRoom(memberId: Long, otherMemberId: Long): Boolean

    /**
     * 두 멤버가 [quizSetId]의 같은 후보 그룹에 **아직 함께 남아 있는지** — 성사 전 관계 판정.
     * 어느 한쪽이라도 그 그룹을 거절했으면 같은 그룹이 될 일이 없으므로 제외한다.
     */
    fun existsSharedCandidateGroup(memberId: Long, otherMemberId: Long, quizSetId: Long): Boolean

    /**
     * 주어진 그룹의 대기 초대를 거절로 바꾸고 바꾼 행 수를 돌려준다. GroupMatchMember.decline()과 같은 전이다.
     * 상태 조건이 UPDATE 안에 있어서 동시에 커밋된 수락을 덮지 않는다. 엔티티를 거치지 않으므로
     * 호출 트랜잭션에 이미 올라온 초대는 갱신되지 않는다.
     */
    fun declinePendingInvitations(groupMatchIds: Collection<Long>, updatedAt: LocalDateTime): Long
}
