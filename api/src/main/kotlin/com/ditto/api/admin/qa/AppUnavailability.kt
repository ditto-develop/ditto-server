package com.ditto.api.admin.qa

import com.ditto.common.exception.ErrorCode
import com.ditto.domain.member.entity.Member
import java.time.LocalDateTime

/** 회원이 앱을 쓸 수 없는 이유. JwtAuthenticationFilter 와 같은 순서·오류 코드로 판별하고, 콘솔도 이 회원으로는 움직이지 않는다. */
class AppUnavailability private constructor(
    val reason: Reason,
    val text: String,
) {
    /** 탈퇴는 제재가 아니라 제재 관리에서 풀 수 없다. */
    enum class Reason(val errorCode: ErrorCode, val isSanction: Boolean) {
        LEFT(ErrorCode.MEMBER_LEFT, isSanction = false),
        BANNED(ErrorCode.MEMBER_BANNED, isSanction = true),
        SUSPENDED(ErrorCode.MEMBER_SUSPENDED, isSanction = true),
    }

    val isSanction: Boolean = reason.isSanction

    companion object {
        /** 더미는 가입을 마친 채 만들어져 PENDING 은 보지 않는다. */
        fun of(member: Member, now: LocalDateTime): AppUnavailability? = when {
            member.isLeft() -> AppUnavailability(Reason.LEFT, "탈퇴")
            member.isBanned() -> AppUnavailability(Reason.BANNED, "영구 차단")
            member.isSuspendedAt(now) -> AppUnavailability(Reason.SUSPENDED, suspensionTextOf(member))
            else -> null
        }

        private fun suspensionTextOf(member: Member): String {
            val until = member.suspendedUntil ?: return "이용 정지"
            return "이용 정지 ~${QaTimeFormat.format(until)}"
        }
    }
}
