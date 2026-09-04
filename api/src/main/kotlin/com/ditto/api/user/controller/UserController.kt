package com.ditto.api.user.controller

import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.api.user.dto.CheckNicknameResponse
import com.ditto.api.user.dto.CreateUserRequest
import com.ditto.api.user.dto.LeaveRequest
import com.ditto.api.user.dto.LeaveResponse
import com.ditto.api.user.dto.MeResponse
import com.ditto.api.user.dto.PublicProfileResponse
import com.ditto.api.user.dto.RegisterResponse
import com.ditto.api.user.dto.UpdatePersonalInfoRequest
import com.ditto.api.user.service.UserService
import com.ditto.common.logging.Loggable
import com.ditto.common.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class UserController(
    private val userService: UserService,
) {

    @PostMapping("/api/v1/users")
    fun register(
        @Valid @RequestBody request: CreateUserRequest,
        @AuthenticationPrincipal principal: MemberPrincipal,
    ): ApiResponse<RegisterResponse> {
        val result = userService.register(principal.memberId, request)
        return ApiResponse.ok(result)
    }

    @GetMapping("/api/v1/users/me")
    fun getMe(@AuthenticationPrincipal principal: MemberPrincipal): ApiResponse<MeResponse> {
        val result = userService.getMe(principal.memberId)
        return ApiResponse.ok(result)
    }

    /**
     * 가입 때 못 받은 신원 정보(이름·전화번호·이메일·생년월일)를 나중에 채운다.
     * 준 항목만 반영하며 몇 번이든 호출할 수 있다.
     *
     * 경로를 `/users/me`와 분리한 이유: `JwtAuthenticationFilter`가 HTTP method를 무시하고 경로만으로
     * PENDING 허용을 판정하므로(`SecurityConfig.PENDING_ALLOWED_PATHS`), 같은 경로에 두면 가입 미완료
     * 회원에게도 함께 열린다. 이 API는 가입을 마친 회원의 보완 창구다.
     */
    @Loggable
    @PatchMapping("/api/v1/users/me/personal-info")
    fun updatePersonalInfo(
        @Valid @RequestBody request: UpdatePersonalInfoRequest,
        @AuthenticationPrincipal principal: MemberPrincipal,
    ): ApiResponse<MeResponse> {
        val result = userService.updatePersonalInfo(principal.memberId, request)
        return ApiResponse.ok(result)
    }

    @GetMapping("/api/v1/users/{id}/profile")
    fun getPublicProfile(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: MemberPrincipal,
    ): ApiResponse<PublicProfileResponse> {
        val result = userService.getPublicProfile(principal.memberId, id)
        return ApiResponse.ok(result)
    }

    @GetMapping("/api/v1/users/nickname/{nickname}/availability")
    fun checkNicknameAvailability(@PathVariable nickname: String): ApiResponse<CheckNicknameResponse> {
        val result = userService.checkNicknameAvailability(nickname)
        return ApiResponse.ok(result)
    }

    @PostMapping("/api/v1/users/{id}/leave")
    fun leaveUser(
        @PathVariable id: Long,
        @Valid @RequestBody(required = false) request: LeaveRequest?,
        @AuthenticationPrincipal principal: MemberPrincipal,
    ): ApiResponse<LeaveResponse> {
        val result = userService.leaveUser(id, principal.memberId, request ?: LeaveRequest())
        return ApiResponse.ok(result)
    }
}
