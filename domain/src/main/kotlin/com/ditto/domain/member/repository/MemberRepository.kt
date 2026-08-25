package com.ditto.domain.member.repository

import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberRole
import com.ditto.domain.member.entity.MemberStatus
import jakarta.persistence.LockModeType
import java.time.LocalDateTime
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

interface MemberRepository : JpaRepository<Member, Long> {

    /**
     * 회원 행을 잠그고 읽는다 — 가입 완료(register)처럼 "상태 확인 후 전이 + 파생 행 생성"이
     * 한 덩어리여야 하는 경로용. 잠그지 않으면 같은 회원의 가입 요청이 겹칠 때(더블 탭·재시도)
     * 둘 다 PENDING 스냅샷을 읽고 통과해, 소개노트 같은 파생 행의 유니크 충돌이 5xx 로 새어 나간다.
     *
     * MANDATORY 인 이유는 ADR 0011 규칙 6 과 같다 — 호출자 트랜잭션이 없으면 잠금이 즉시 풀려
     * 유실이 조용히 일어나는 대신 예외로 드러낸다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Transactional(propagation = Propagation.MANDATORY)
    fun findWithLockById(id: Long): Member?

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
