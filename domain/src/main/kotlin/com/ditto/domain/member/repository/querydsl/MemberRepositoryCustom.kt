package com.ditto.domain.member.repository.querydsl

import com.ditto.domain.member.entity.MemberStatus
import java.time.LocalDateTime

interface MemberRepositoryCustom {

    /** 해당 상태인 회원의 ID 전체. 전원 대상 알림(퀴즈 오픈 등)이 쓴다. 엔티티를 올리지 않는다. */
    fun findAllIdsByStatus(status: MemberStatus): List<Long>

    /** 탈퇴한 회원도 LEFT 로 들어 있다. */
    fun countJoinedMembersByStatus(filter: MemberFilter): Map<MemberStatus, Long>

    fun findAllJoinedAtsWithin(joinedAtRange: OpenEndRange<LocalDateTime>, filter: MemberFilter): List<LocalDateTime>

    /** 현재 회원은 가입을 마쳤고 탈퇴하지 않은 회원이다. 정지 회원도 들어간다. */
    fun countCurrentMembersByGenderAndBirthDate(filter: MemberFilter): List<GenderBirthDateCount>
}
