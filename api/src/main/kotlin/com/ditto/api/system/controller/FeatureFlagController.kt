package com.ditto.api.system.controller

import com.ditto.api.system.dto.FeatureFlagsResponse
import com.ditto.api.system.featureflag.FeatureFlagKeyQuery
import com.ditto.api.system.featureflag.FeatureFlags
import com.ditto.common.logging.Loggable
import com.ditto.common.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@Loggable
class FeatureFlagController(
    private val featureFlags: FeatureFlags,
) {
    /**
     * FE 가 화면을 보이거나 감추려고 읽는 서버 기능 플래그. FE 가 필요한 키만 묻고, 서버는 그 키만 답한다.
     * 로그인 전에도 부르므로 API Key 만 받는다(SecurityConfig @Order(4)). 서버 쪽 1분 캐시([FeatureFlags])를 그대로 거친다.
     * `keys` 는 반복(`keys=A&keys=B`)과 쉼표(`keys=A,B`) 둘 다 받는다. 배경: ADR 0051.
     */
    @GetMapping("/api/v1/feature-flags")
    fun getFeatureFlags(
        @RequestParam(required = false) keys: List<String>?,
    ): ApiResponse<FeatureFlagsResponse> =
        ApiResponse.ok(FeatureFlagsResponse(featureFlags.statesOf(FeatureFlagKeyQuery.normalize(keys))))
}
