package com.ditto.domain.member.repository

import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberRole
import org.springframework.data.jpa.repository.JpaRepository

interface MemberRepository : JpaRepository<Member, Long> {
    fun existsByNickname(nickname: String): Boolean

    /** 이메일 정확 일치 회원 목록(같은 이메일에 여러 명 가능). */
    fun findByEmailOrderByIdAsc(email: String): List<Member>

    /** 특정 권한을 가진 회원 목록(어드민 보유자 조회 등). */
    fun findByRoleOrderByIdAsc(role: MemberRole): List<Member>

    /** 닉네임 접두사로 시작하는 회원 목록(더미 식별·일괄 정리용). */
    fun findByNicknameStartingWith(prefix: String): List<Member>

    /** 닉네임 접두사로 시작하는 회원 수(더미 현황 표시용). */
    fun countByNicknameStartingWith(prefix: String): Long
}
