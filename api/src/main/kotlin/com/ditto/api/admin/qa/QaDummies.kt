package com.ditto.api.admin.qa

import com.ditto.api.admin.dummy.AdminDummyService
import com.ditto.api.admin.qa.dto.QaMember
import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.member.repository.MemberRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** QA 콘솔이 대신 움직일 수 있는 더미 회원. 닉네임 규칙이 '-'를 막아 실회원은 더미 접두어를 가질 수 없다. */
@Component
@Transactional(readOnly = true)
class QaDummies(
    private val memberRepository: MemberRepository,
) {
    fun findIds(): Set<Long> =
        memberRepository.findByNicknameStartingWith(AdminDummyService.NICKNAME_PREFIX)
            .map { it.id }
            .toSet()

    /** 결과 메시지에 쓸 이름표. 더미가 아니거나 없는 회원이어도 준다. 막는 일은 [principalOf]가 한다. */
    fun memberOf(memberId: Long): QaMember =
        QaMembers(listOfNotNull(memberRepository.findByIdOrNull(memberId))).of(memberId)

    fun principalOf(memberId: Long): MemberPrincipal {
        val member = memberRepository.findByIdOrNull(memberId) ?: throw WarnException(ErrorCode.NOT_FOUND)
        if (!member.nickname.startsWith(AdminDummyService.NICKNAME_PREFIX)) {
            throw WarnException(ErrorCode.FORBIDDEN, "더미 회원만 대신 움직일 수 있습니다.")
        }
        return MemberPrincipal(member.id)
    }
}
