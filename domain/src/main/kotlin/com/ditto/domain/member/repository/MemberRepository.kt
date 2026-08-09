package com.ditto.domain.member.repository

import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberRole
import com.ditto.domain.member.entity.MemberStatus
import java.time.LocalDateTime
import org.springframework.data.jpa.repository.JpaRepository

interface MemberRepository : JpaRepository<Member, Long> {
    fun existsByNickname(nickname: String): Boolean

    /** 주어진 ID 중 해당 상태인 회원 수 — 탈퇴자가 섞였는지 확인하는 데 쓴다. */
    fun countByIdInAndStatus(ids: Collection<Long>, status: MemberStatus): Long

    /** 특정 상태이면서 정지 해제 예정일이 지난 회원 목록 (만료 원복 배치용). */
    fun findAllByStatusAndSuspendedUntilLessThanEqual(status: MemberStatus, until: LocalDateTime): List<Member>

    /** 탈퇴 보존 기간이 지난 회원을 찾는 삭제 배치용 — leftAt이 [leftBefore] 이전인 LEFT 회원. */
    fun findAllByStatusAndLeftAtLessThanEqual(status: MemberStatus, leftBefore: LocalDateTime): List<Member>

    /** 이메일 정확 일치 회원 목록(같은 이메일에 여러 명 가능). */
    fun findByEmailOrderByIdAsc(email: String): List<Member>

    /** 특정 권한을 가진 회원 목록(어드민 보유자 조회 등). */
    fun findByRoleOrderByIdAsc(role: MemberRole): List<Member>

    /** 닉네임 접두사로 시작하는 회원 목록. */
    fun findByNicknameStartingWith(prefix: String): List<Member>

    /** 닉네임 접두사로 시작하는 회원 수. */
    fun countByNicknameStartingWith(prefix: String): Long
}
