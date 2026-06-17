package com.ditto.api.admin.member

import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberRole
import com.ditto.domain.member.repository.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 어드민 회원 운영 — 이메일 검색 및 권한(Role) 변경.
 */
@Service
@Transactional
class AdminMemberService(
    private val memberRepository: MemberRepository,
) {
    /** 이메일 정확 일치 검색. 입력의 공백은 모두 제거 후 매칭한다. */
    @Transactional(readOnly = true)
    fun searchByEmail(email: String): List<Member> {
        val normalized = email.filterNot { it.isWhitespace() }
        if (normalized.isEmpty()) return emptyList()
        return memberRepository.findByEmailOrderByIdAsc(normalized)
    }

    /** 회원 권한을 변경한다. */
    fun changeRole(memberId: Long, role: MemberRole) {
        val member = memberRepository.findById(memberId).orElseThrow { WarnException(ErrorCode.NOT_FOUND) }
        member.changeRole(role)
    }
}
