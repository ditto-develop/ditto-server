package com.ditto.api.admin.qa

import com.ditto.api.admin.dummy.DummyMarker
import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.repository.MemberRepository
import java.time.LocalDateTime
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** QA 콘솔이 대신 움직일 수 있는 더미 회원([DummyMarker]). */
@Component
@Transactional(readOnly = true)
class QaDummies(
    private val memberRepository: MemberRepository,
) {
    fun findIds(): Set<Long> =
        memberRepository.findByNicknameStartingWith(DummyMarker.NICKNAME_PREFIX)
            .map { it.id }
            .toSet()

    fun principalOf(memberId: Long): MemberPrincipal = MemberPrincipal(findDummy(memberId).id)

    /** 앱 인증 필터처럼 탈퇴·영구 차단·정지 중인 더미는 거부한다. 제재받은 더미가 다시 움직이는 흐름에서 쓴다. */
    fun activePrincipalOf(memberId: Long, now: LocalDateTime): MemberPrincipal {
        val dummy = findDummy(memberId)
        if (dummy.isLeft()) throw WarnException(ErrorCode.MEMBER_LEFT)
        if (dummy.isBanned()) throw WarnException(ErrorCode.MEMBER_BANNED)
        if (dummy.isSuspendedAt(now)) throw WarnException(ErrorCode.MEMBER_SUSPENDED)
        return MemberPrincipal(dummy.id)
    }

    private fun findDummy(memberId: Long): Member {
        val member = memberRepository.findByIdOrNull(memberId) ?: throw WarnException(ErrorCode.NOT_FOUND, "없는 회원입니다: #$memberId")
        if (!DummyMarker.isDummy(member.nickname)) {
            throw WarnException(ErrorCode.FORBIDDEN, "더미 회원만 대신 움직일 수 있습니다.")
        }
        return member
    }
}
