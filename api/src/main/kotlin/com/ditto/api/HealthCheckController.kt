package com.ditto.api

import org.springframework.boot.availability.ApplicationAvailability
import org.springframework.boot.availability.ReadinessState
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 로드밸런서(ALB 대상 그룹) 헬스체크. 트래픽을 받을 준비가 됐는지(readiness)를 답한다.
 *
 * 늘 200 을 주면 기동이 끝나기 전이나 종료가 시작된 뒤에도 정상으로 보인다(#361). Boot 는 기동을 마치면
 * `ACCEPTING_TRAFFIC`, 종료를 시작하면 `REFUSING_TRAFFIC`으로 바꾸므로 그 상태를 그대로 따른다.
 * 로드밸런서는 상태 코드만 보므로 `ApiResponse` 래핑·항상 200 규칙의 예외다.
 */
@RestController
class HealthCheckController(
    private val applicationAvailability: ApplicationAvailability,
) {

    @GetMapping("/health")
    fun ok(): ResponseEntity<String> =
        if (applicationAvailability.readinessState == ReadinessState.ACCEPTING_TRAFFIC) {
            ResponseEntity.ok("OK")
        } else {
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("REFUSING_TRAFFIC")
        }
}
