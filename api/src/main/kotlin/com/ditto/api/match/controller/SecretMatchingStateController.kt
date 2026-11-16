package com.ditto.api.match.controller

import com.ditto.api.config.auth.MemberPrincipal
import com.ditto.api.match.dto.SecretMatchingStateResponse
import com.ditto.api.match.service.SecretMatchingStateService
import com.ditto.api.system.ServerTimeProvider
import com.ditto.common.logging.Loggable
import com.ditto.common.response.ApiResponse
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Loggable
class SecretMatchingStateController(
    private val secretMatchingStateService: SecretMatchingStateService,
    private val serverTimeProvider: ServerTimeProvider,
) {

    @GetMapping("/api/v1/matching/secret-state")
    fun getSecretMatchingState(
        @AuthenticationPrincipal principal: MemberPrincipal,
    ): ApiResponse<SecretMatchingStateResponse> =
        ApiResponse.ok(secretMatchingStateService.getSecretMatchingState(principal.memberId, serverTimeProvider.now()))
}
