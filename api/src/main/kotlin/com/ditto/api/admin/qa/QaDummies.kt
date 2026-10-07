package com.ditto.api.admin.qa

import com.ditto.api.admin.dummy.DummyMarker
import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.api.system.ServerTimeProvider
import com.ditto.common.exception.ErrorCode
import com.ditto.common.exception.WarnException
import com.ditto.domain.member.entity.Member
import com.ditto.domain.member.repository.MemberRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** QA 콘솔이 대신 움직일 수 있는 더미 회원([DummyMarker]). */
@Component
@Transactional(readOnly = true)
class QaDummies(
    private val memberRepository: MemberRepository,
    private val serverTimeProvider: ServerTimeProvider,
) {
    fun findIds(): Set<Long> =
        memberRepository.findByNicknameStartingWith(DummyMarker.NICKNAME_PREFIX)
            .map { it.id }
            .toSet()

    /** 앱 컨트롤러를 직접 부르면 인증 필터를 거치지 않아, 필터가 막는 더미를 여기서 같은 오류 코드로 거부한다. */
    fun requireActiveDummyPrincipal(memberId: Long): MemberPrincipal {
        val dummy = requireDummy(memberId)
        AppUnavailability.of(dummy, serverTimeProvider.now())?.let { throw it.rejectionOf(dummy) }
        return MemberPrincipal(dummy.id)
    }

    private fun AppUnavailability.rejectionOf(dummy: Member): WarnException {
        val hint = if (isSanction) " 제재 관리에서 해제하세요." else ""
        return WarnException(reason.errorCode, "이 더미(${dummy.nickname})는 $text 상태라 앱처럼 움직일 수 없습니다.$hint")
    }

    private fun requireDummy(memberId: Long): Member {
        val member = memberRepository.findByIdOrNull(memberId)
            ?: throw WarnException(ErrorCode.NOT_FOUND, "없는 회원입니다: #$memberId")
        if (!DummyMarker.isDummy(member.nickname)) {
            throw WarnException(ErrorCode.FORBIDDEN, "더미 회원만 대신 움직일 수 있습니다.")
        }
        return member
    }
}
