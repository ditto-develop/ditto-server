package com.ditto.api.system.controller

import com.ditto.api.system.dto.SystemStateResponse
import com.ditto.api.system.SystemStateProvider
import com.ditto.common.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class SystemController(
    private val systemStateProvider: SystemStateProvider,
) {
    /** 현재 서버 시각 기준의 연/월/주차/기간 상태. 어드민 시각 오버라이드가 반영된다. */
    @GetMapping("/api/v1/system/state")
    fun getSystemState(): ApiResponse<SystemStateResponse> =
        ApiResponse.ok(SystemStateResponse.from(systemStateProvider.current()))
}
