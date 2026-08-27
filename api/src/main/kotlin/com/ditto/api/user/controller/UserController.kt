package com.ditto.api.user.controller

import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.api.user.dto.AnswerMatchResponse
import com.ditto.api.user.dto.CheckNicknameResponse
import com.ditto.api.user.dto.CreateUserRequest
import com.ditto.api.user.dto.LeaveRequest
import com.ditto.api.user.dto.LeaveResponse
import com.ditto.api.user.dto.MeResponse
import com.ditto.api.user.dto.MyRatingsResponse
import com.ditto.api.user.dto.PublicProfileResponse
import com.ditto.api.user.dto.RegisterResponse
import com.ditto.api.user.service.PeerProfileService
import com.ditto.api.user.service.UserService
import com.ditto.common.logging.Loggable
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
    private val peerProfileService: PeerProfileService,
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

    @GetMapping("/api/v1/users/{id}/profile")
    fun getPublicProfile(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: MemberPrincipal,
    ): ApiResponse<PublicProfileResponse> {
        val result = userService.getPublicProfile(principal.memberId, id)
        return ApiResponse.ok(result)
    }

    /**
     * 상대가 받은 평가. 내 평가 조회(`/users/me/ratings`)와 같은 스키마·같은 공개 기준(3건)이다.
     */
    @Loggable
    @GetMapping("/api/v1/users/{id}/ratings")
    fun getUserRatings(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: MemberPrincipal,
    ): ApiResponse<MyRatingsResponse> {
        val result = peerProfileService.getRatings(principal.memberId, id)
        return ApiResponse.ok(result)
    }

    /**
     * 상대와 나의 퀴즈 답변 일치 요약("나와 같은 답" 배지용).
     * 상대가 무엇을 골랐는지는 내려주지 않는다 — 화면이 쓰는 것은 일치 개수뿐이다.
     */
    @Loggable
    @GetMapping("/api/v1/users/{id}/answers")
    fun getUserAnswerMatch(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: MemberPrincipal,
    ): ApiResponse<AnswerMatchResponse> {
        val result = peerProfileService.getAnswerMatch(principal.memberId, id)
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
