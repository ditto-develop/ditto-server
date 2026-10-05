package com.ditto.api.admin.member

import com.ditto.api.admin.member.dto.MemberSearchResult
import com.ditto.api.admin.member.dto.MemberSummary
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.entity.MemberRole
import com.ditto.domain.member.repository.MemberRepository
import org.springframework.data.domain.Limit
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 어드민 회원 운영. 닉네임·회원 ID·이메일 검색과 권한(Role) 변경. */
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

    /** `#123`은 회원 ID 정확 일치, 그 외는 닉네임 부분 일치(최근 가입 순)다. */
    @Transactional(readOnly = true)
    fun searchByNicknameOrId(query: String): MemberSearchResult {
        val keyword = query.trim()
        if (keyword.isEmpty()) return MemberSearchResult.EMPTY
        if (keyword.startsWith(MEMBER_ID_PREFIX)) {
            val member = findMemberByIdText(keyword.removePrefix(MEMBER_ID_PREFIX))
            return MemberSearchResult(listOfNotNull(member).map { MemberSummary.of(it) }, isTruncated = false)
        }

        // 한 명 더 읽어 제한을 넘었는지 안다.
        val limit = Limit.of(NICKNAME_SEARCH_LIMIT + 1)
        val members = memberRepository.findByNicknameContainingOrderByIdDesc(keyword, limit)
        return MemberSearchResult(
            members.take(NICKNAME_SEARCH_LIMIT).map { MemberSummary.of(it) },
            isTruncated = members.size > NICKNAME_SEARCH_LIMIT,
        )
    }

    /** 현재 ADMIN 권한 보유 회원 목록. */
    @Transactional(readOnly = true)
    fun listAdmins(): List<Member> = memberRepository.findByRoleOrderByIdAsc(MemberRole.ADMIN)

    /** 회원 권한을 변경한다. */
    fun changeRole(memberId: Long, role: MemberRole) {
        val member = memberRepository.findById(memberId).orElseThrow { WarnException(ErrorCode.NOT_FOUND) }
        member.changeRole(role)
    }

    private fun findMemberByIdText(idText: String): Member? {
        val memberId = idText.toLongOrNull() ?: return null
        return memberRepository.findById(memberId).orElse(null)
    }

    companion object {
        const val NICKNAME_SEARCH_LIMIT = 50
        private const val MEMBER_ID_PREFIX = "#"
    }
}
