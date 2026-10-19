package com.ditto.api.chat.websocket

import com.ditto.api.support.IntegrationTest
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.MeterRegistry
import javax.sql.DataSource

class ChatWebSocketMetricsTest(
    private val meterRegistry: MeterRegistry,
    dataSource: DataSource,
) : IntegrationTest(dataSource, {

    "WebSocket 세션 메트릭" - {

        "접속이 없으면 현재 세션 수는 0이다" {
            meterRegistry.find(ChatWebSocketMetrics.SESSIONS).gauge().shouldNotBeNull().value() shouldBe 0.0
        }

        "비정상 종료 사유 3종이 카운터로 등록된다" {
            listOf("limit_exceeded", "no_messages_received", "transport_error").forEach { reason ->
                meterRegistry.find(ChatWebSocketMetrics.SESSIONS_CLOSED).tag("reason", reason)
                    .functionCounter().shouldNotBeNull()
            }
        }

        "STOMP 연결 프레임 3종이 카운터로 등록된다" {
            listOf("connect", "connected", "disconnect").forEach { frame ->
                meterRegistry.find(ChatWebSocketMetrics.STOMP_FRAMES).tag("frame", frame)
                    .functionCounter().shouldNotBeNull()
            }
        }
    }
})
