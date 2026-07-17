package com.ditto.api.sanction.controller

import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.api.sanction.dto.MySanctionResponse
import com.ditto.api.sanction.service.MySanctionService
import com.ditto.api.system.ServerTimeProvider
import com.ditto.common.response.ApiResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class MySanctionController(
    private val mySanctionService: MySanctionService,
    private val serverTimeProvider: ServerTimeProvider,
) {

    @GetMapping("/api/v1/users/me/sanction")
    fun getMySanction(
        @AuthenticationPrincipal principal: MemberPrincipal,
    ): ApiResponse<MySanctionResponse> {
        val result = mySanctionService.getMySanction(principal.memberId, serverTimeProvider.now())
        return ApiResponse.ok(result)
    }
}
