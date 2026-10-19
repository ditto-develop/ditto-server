package com.ditto.api.support.ratelimit

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import java.time.Duration

class FixedWindowRateLimiterTest {

    private var now = 0L
    private val limiter = FixedWindowRateLimiter(limit = 2, window = Duration.ofMinutes(1), nowMillis = { now })

    @Test
    @DisplayName("윈도우 안에서 한도까지만 허용하고 키마다 따로 센다")
    fun limitPerKey() {
        limiter.tryAcquire("a") shouldBe true
        limiter.tryAcquire("a") shouldBe true
        limiter.tryAcquire("a") shouldBe false
        limiter.tryAcquire("b") shouldBe true
    }

    @Test
    @DisplayName("윈도우가 지나면 다시 허용한다")
    fun resetAfterWindow() {
        repeat(3) { limiter.tryAcquire("a") }

        now += Duration.ofMinutes(1).toMillis()

        limiter.tryAcquire("a") shouldBe true
    }

    @Test
    @DisplayName("클라이언트 IP는 X-Forwarded-For 맨 끝 값이고, 없으면 remoteAddr")
    fun clientIp() {
        val forwarded = MockHttpServletRequest().apply {
            addHeader("X-Forwarded-For", "6.6.6.6, 198.51.100.7")
            remoteAddr = "10.0.0.1"
        }
        val direct = MockHttpServletRequest().apply { remoteAddr = "10.0.0.2" }

        ClientIp.of(forwarded) shouldBe "198.51.100.7"
        ClientIp.of(direct) shouldBe "10.0.0.2"
    }
}
