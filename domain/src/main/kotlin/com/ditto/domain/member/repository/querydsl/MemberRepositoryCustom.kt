package com.ditto.domain.member.repository.querydsl

import com.ditto.domain.member.entity.MemberStatus

interface MemberRepositoryCustom {

    /** 해당 상태인 회원의 ID 전체. 전원 대상 알림(퀴즈 오픈 등)이 쓴다. 엔티티를 올리지 않는다. */
    fun findAllIdsByStatus(status: MemberStatus): List<Long>
}
