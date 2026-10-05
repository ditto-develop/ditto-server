package com.ditto.api.admin.member

import com.ditto.api.admin.member.dto.MemberSearchResult
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberRole
import com.ditto.domain.member.repository.MemberRepository
import org.springframework.data.domain.Limit
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

    /** `#123`은 회원 ID 정확 일치, 그 외는 닉네임 부분 일치다. */
    @Transactional(readOnly = true)
    fun searchByNicknameOrId(query: String): List<MemberSearchResult> {
        val keyword = query.trim()
        if (keyword.isEmpty()) return emptyList()
        if (keyword.startsWith(MEMBER_ID_PREFIX)) {
            val member = findMemberById(keyword.removePrefix(MEMBER_ID_PREFIX))
            return listOfNotNull(member).map { MemberSearchResult.of(it) }
        }
        return memberRepository.findByNicknameContainingOrderByIdAsc(keyword, Limit.of(SEARCH_LIMIT))
            .map { MemberSearchResult.of(it) }
    }

    /** 현재 ADMIN 권한 보유 회원 목록. */
    @Transactional(readOnly = true)
    fun listAdmins(): List<Member> = memberRepository.findByRoleOrderByIdAsc(MemberRole.ADMIN)

    /** 회원 권한을 변경한다. */
    fun changeRole(memberId: Long, role: MemberRole) {
        val member = memberRepository.findById(memberId).orElseThrow { WarnException(ErrorCode.NOT_FOUND) }
        member.changeRole(role)
    }

    private fun findMemberById(text: String): Member? {
        val memberId = text.toLongOrNull() ?: return null
        return memberRepository.findById(memberId).orElse(null)
    }

    companion object {
        const val SEARCH_LIMIT = 50
        private const val MEMBER_ID_PREFIX = "#"
    }
}
