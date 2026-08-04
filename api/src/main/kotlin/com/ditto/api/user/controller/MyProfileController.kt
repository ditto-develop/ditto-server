package com.ditto.api.user.controller

import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.api.user.dto.MyRatingsResponse
import com.ditto.api.user.dto.MyStatsResponse
import com.ditto.api.user.dto.PublicProfileResponse
import com.ditto.api.user.dto.UpdateMyProfileRequest
import com.ditto.api.user.service.MyProfileService
import com.ditto.common.logging.Loggable
import com.ditto.common.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

/**
 * 마이프로필 화면 전용 엔드포인트. 타인 프로필(`/users/{id}/profile`)은 [UserController]에 있다.
 */
@RestController
class MyProfileController(
    private val myProfileService: MyProfileService,
) {

    @Loggable
    @GetMapping("/api/v1/users/me/profile")
    fun getMyProfile(
        @AuthenticationPrincipal principal: MemberPrincipal,
    ): ApiResponse<PublicProfileResponse> {
        val result = myProfileService.getMyProfile(principal.memberId)
        return ApiResponse.ok(result)
    }

    @Loggable
    @PatchMapping("/api/v1/users/me/profile")
    fun updateMyProfile(
        @Valid @RequestBody request: UpdateMyProfileRequest,
        @AuthenticationPrincipal principal: MemberPrincipal,
    ): ApiResponse<PublicProfileResponse> {
        val result = myProfileService.updateMyProfile(principal.memberId, request)
        return ApiResponse.ok(result)
    }

    @Loggable
    @GetMapping("/api/v1/users/me/stats")
    fun getMyStats(
        @AuthenticationPrincipal principal: MemberPrincipal,
    ): ApiResponse<MyStatsResponse> {
        val result = myProfileService.getMyStats(principal.memberId)
        return ApiResponse.ok(result)
    }

    @Loggable
    @GetMapping("/api/v1/users/me/ratings")
    fun getMyRatings(
        @AuthenticationPrincipal principal: MemberPrincipal,
    ): ApiResponse<MyRatingsResponse> {
        val result = myProfileService.getMyRatings(principal.memberId)
        return ApiResponse.ok(result)
    }
}
