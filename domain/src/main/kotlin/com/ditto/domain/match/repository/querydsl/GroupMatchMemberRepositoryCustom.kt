package com.ditto.domain.match.repository.querydsl

import com.ditto.domain.member.repository.querydsl.MemberFilter
import java.time.LocalDate

interface GroupMatchMemberRepositoryCustom {

    fun existsByMemberIdAndQuizSetId(memberId: Long, quizSetId: Long): Boolean

    /** [weekStartedOn] 주차의 아직 성사되지 않은 그룹에 수락해 둔 초대가 있는지. 탈퇴 제한 판단에 쓴다. */
    fun existsAcceptedInUnformedGroupOfWeek(memberId: Long, weekStartedOn: LocalDate): Boolean

    /** [weekStartedOn] 주차의 성사된 그룹에 수락했는지. 그 뒤 채팅방을 나갔어도 참이다. */
    fun existsAcceptedInFormedGroupOfWeek(memberId: Long, weekStartedOn: LocalDate): Boolean

    /** 두 멤버가 같은 그룹 채팅방에 **함께 들어갔는지** — 양쪽 수락 + 그룹 성사. 성사 후 관계 판정. */
    fun existsSharedRoom(memberId: Long, otherMemberId: Long): Boolean

    /**
     * 두 멤버가 [quizSetId]의 같은 후보 그룹에 **아직 함께 남아 있는지** — 성사 전 관계 판정.
     * 어느 한쪽이라도 그 그룹을 거절했으면 같은 그룹이 될 일이 없으므로 제외한다.
     */
    fun existsSharedCandidateGroup(memberId: Long, otherMemberId: Long, quizSetId: Long): Boolean

    /** 그 주 그룹 초대를 그룹 성사 여부·초대 상태·성별로 묶은 수. */
    fun countInvitedMembersInWeek(weekStartedOn: LocalDate, filter: MemberFilter): List<GroupMatchMemberCount>

    /** 그 주 성사된 그룹마다 수락한 회원을 성별로 센다. 그룹 단위라 회원 조건과 관리자 제외를 걸지 않는다. */
    fun countAcceptedMembersPerActiveGroupInWeek(weekStartedOn: LocalDate): List<ActiveGroupMemberCount>
}
