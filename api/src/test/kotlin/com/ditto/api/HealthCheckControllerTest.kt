package com.ditto.api

import com.ditto.api.support.ControllerUnitTest
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.availability.ApplicationAvailability
import org.springframework.boot.availability.ReadinessState
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class HealthCheckControllerTest : ControllerUnitTest() {

    private val applicationAvailability: ApplicationAvailability = mockk()

    override val controller = HealthCheckController(applicationAvailability)

    @Test
    @DisplayName("트래픽을 받을 준비가 됐으면 200 을 준다")
    fun okWhenAcceptingTraffic() {
        every { applicationAvailability.readinessState } returns ReadinessState.ACCEPTING_TRAFFIC

        mockMvc.perform(get("/health"))
            .andExpect(status().isOk)
    }

    @Test
    @DisplayName("종료를 시작해 트래픽을 받지 않으면 503 을 준다")
    fun unavailableWhenRefusingTraffic() {
        every { applicationAvailability.readinessState } returns ReadinessState.REFUSING_TRAFFIC

        mockMvc.perform(get("/health"))
            .andExpect(status().isServiceUnavailable)
    }
}
