package com.ditto.domain.member.repository

import com.ditto.domain.member.entity.Member
import org.springframework.data.jpa.repository.JpaRepository

interface MemberRepository : JpaRepository<Member, Long> {
    fun existsByNickname(nickname: String): Boolean

    /** 이메일 정확 일치 회원 목록(같은 이메일에 여러 명 가능). */
    fun findByEmailOrderByIdAsc(email: String): List<Member>
}
