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

    /**
     * 앱 컨트롤러를 직접 부르면 인증 필터를 거치지 않아, 필터처럼 탈퇴·영구 차단·정지 중인 더미를 여기서 거부한다.
     * JwtAuthenticationFilter 와 같은 순서·오류 코드로 유지한다. 더미는 가입을 마친 채 만들어져 PENDING 검사는 없다.
     */
    fun requireActiveDummyPrincipal(memberId: Long): MemberPrincipal {
        val dummy = requireDummy(memberId)
        if (dummy.isLeft()) throw WarnException(ErrorCode.MEMBER_LEFT, "이 더미는 탈퇴해 앱처럼 움직일 수 없습니다.")
        if (dummy.isBanned()) throw dummy.restrictedRejection(ErrorCode.MEMBER_BANNED, "영구 차단")
        if (dummy.isSuspendedAt(serverTimeProvider.now())) {
            throw dummy.restrictedRejection(ErrorCode.MEMBER_SUSPENDED, "이용 정지")
        }
        return MemberPrincipal(dummy.id)
    }

    private fun Member.restrictedRejection(errorCode: ErrorCode, sanctionName: String) =
        WarnException(errorCode, "이 더미($nickname)는 $sanctionName 중이라 앱처럼 움직일 수 없습니다. 제재 관리에서 해제하세요.")

    private fun requireDummy(memberId: Long): Member {
        val member = memberRepository.findByIdOrNull(memberId)
            ?: throw WarnException(ErrorCode.NOT_FOUND, "없는 회원입니다: #$memberId")
        if (!DummyMarker.isDummy(member.nickname)) {
            throw WarnException(ErrorCode.FORBIDDEN, "더미 회원만 대신 움직일 수 있습니다.")
        }
        return member
    }
}
