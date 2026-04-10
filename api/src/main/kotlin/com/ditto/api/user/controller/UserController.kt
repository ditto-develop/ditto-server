package com.ditto.api.user.controller

import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.api.user.dto.CheckNicknameResponse
import com.ditto.api.user.dto.CreateUserRequest
import com.ditto.api.user.dto.LeaveResponse
import com.ditto.api.user.dto.RegisterResponse
import com.ditto.api.user.service.UserService
import com.ditto.common.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class UserController(
    private val userService: UserService,
) {

    @PostMapping("/api/v1/users")
    fun register(@Valid @RequestBody request: CreateUserRequest): ApiResponse<RegisterResponse> {
        val result = userService.register(request)
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
        @AuthenticationPrincipal principal: MemberPrincipal,
    ): ApiResponse<LeaveResponse> {
        val result = userService.leaveUser(id, principal)
        return ApiResponse.ok(result)
    }
}
