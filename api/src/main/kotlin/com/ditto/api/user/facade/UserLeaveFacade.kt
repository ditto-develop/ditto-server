package com.ditto.api.user.facade

import com.ditto.api.auth.service.AppleRefreshTokenService
import com.ditto.api.user.dto.LeaveRequest
import com.ditto.api.user.dto.LeaveResponse
import com.ditto.api.user.service.UserService
import org.springframework.stereotype.Component

/**
 * 앱 안 탈퇴. 탈퇴를 커밋한 뒤 애플 토큰을 폐기한다(App Store 계정 삭제 요건).
 * 애플 호출이 실패하거나 느려도 탈퇴 트랜잭션에 영향이 없게 트랜잭션 밖에서 부른다.
 */
@Component
class UserLeaveFacade(
    private val userService: UserService,
    private val appleRefreshTokenService: AppleRefreshTokenService,
) {

    fun leave(id: Long, memberId: Long, request: LeaveRequest): LeaveResponse {
        val response = userService.leaveUser(id, memberId, request)
        appleRefreshTokenService.revokeFor(memberId)
        return response
    }
}
